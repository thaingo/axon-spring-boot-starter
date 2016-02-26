package hm.binkley.spring.axon;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.axonframework.auditing.AuditLogger;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventProcessingMonitor;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiFunction;

import static org.axonframework.auditing.CorrelationAuditDataProvider
        .DEFAULT_CORRELATION_KEY;

@RequiredArgsConstructor
class SpringBootAuditLogger
        implements AuditLogger, EventProcessingMonitor,
        ApplicationEventPublisherAware {
    private final MessageAuditDataProvider provider;
    @Setter
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void logSuccessful(final CommandMessage<?> command,
            final Object returnValue, final List<EventMessage> events) {
        applicationEventPublisher.publishEvent(
                new AuditApplicationEvent(null, "AXON-COMMAND",
                        new SuccessfulDataMap(provider, command, returnValue,
                                events)));
    }

    @Override
    public void logFailed(final CommandMessage<?> command,
            final Throwable failureCause, final List<EventMessage> events) {
        applicationEventPublisher.publishEvent(
                new AuditApplicationEvent(null, "AXON-COMMAND",
                        new FailedDataMap(provider, command, failureCause,
                                events)));
    }

    @Override
    public void onEventProcessingCompleted(
            final List<? extends EventMessage> eventMessages) {
        for (final EventMessage event : eventMessages)
            applicationEventPublisher.publishEvent(
                    new AuditApplicationEvent(null, "AXON-EVENT",
                            new SuccessfulDataMap(provider, event)));
    }

    @Override
    public void onEventProcessingFailed(
            final List<? extends EventMessage> eventMessages,
            final Throwable cause) {
        for (final EventMessage event : eventMessages)
            applicationEventPublisher.publishEvent(
                    new AuditApplicationEvent(null, "AXON-EVENT",
                            new FailedDataMap(provider, event, cause)));
    }

    private static class SuccessfulDataMap
            extends LinkedHashMap<String, Object> {
        SuccessfulDataMap(final MessageAuditDataProvider auditDataProvider,
                final CommandMessage<?> command, final Object returnValue,
                final List<EventMessage> events) {
            putAll(auditDataProvider.provideAuditDataFor(command));
            compute("command-name", throwIfPresent(command.getCommandName()));
            compute(DEFAULT_CORRELATION_KEY,
                    throwIfPresent(command.getIdentifier()));
            compute("command-success", throwIfPresent(true));
            compute("command-return-value", throwIfPresent(returnValue));
            compute("command-events", throwIfPresent(events));
        }

        SuccessfulDataMap(final MessageAuditDataProvider auditDataProvider,
                final EventMessage event) {
            putAll(auditDataProvider.provideAuditDataFor(event));
            compute("event-name",
                    throwIfPresent(event.getPayloadType().getName()));
            compute(DEFAULT_CORRELATION_KEY,
                    throwIfPresent(event.getIdentifier()));
            compute("event-success", throwIfPresent(true));
        }
    }

    private static class FailedDataMap
            extends LinkedHashMap<String, Object> {
        FailedDataMap(final MessageAuditDataProvider auditDataProvider,
                final CommandMessage<?> command, final Throwable failureCause,
                final List<? extends EventMessage> events) {
            putAll(auditDataProvider.provideAuditDataFor(command));
            compute("command-name", throwIfPresent(command.getCommandName()));
            compute(DEFAULT_CORRELATION_KEY,
                    throwIfPresent(command.getIdentifier()));
            compute("command-success", throwIfPresent(false));
            compute("command-failure-cause", throwIfPresent(failureCause));
            compute("command-events", throwIfPresent(events));
        }

        FailedDataMap(final MessageAuditDataProvider auditDataProvider,
                final EventMessage event, final Throwable failureCause) {
            putAll(auditDataProvider.provideAuditDataFor(event));
            compute("event-name",
                    throwIfPresent(event.getPayloadType().getName()));
            compute(DEFAULT_CORRELATION_KEY,
                    throwIfPresent(event.getIdentifier()));
            compute("event-success", throwIfPresent(false));
            compute("event-failure-cause", throwIfPresent(failureCause));
        }
    }

    private static AuditDataPutter throwIfPresent(final Object value) {
        return new AuditDataPutter(value);
    }

    @RequiredArgsConstructor
    private static final class AuditDataPutter
            implements BiFunction<String, Object, Object> {
        private final Object value;

        @Override
        public Object apply(final String key, final Object oldValue) {
            if (null == oldValue)
                return value;
            throw new DuplicateAuditKeyException(key);
        }
    }
}
