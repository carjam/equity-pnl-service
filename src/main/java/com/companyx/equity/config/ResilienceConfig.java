package com.companyx.equity.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ResilienceConfig {

    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer() {
        return new RegistryEventConsumer<CircuitBreaker>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
                CircuitBreaker circuitBreaker = entryAddedEvent.getAddedEntry();
                circuitBreaker.getEventPublisher()
                        .onStateTransition(event -> 
                            log.warn("Circuit Breaker '{}' transitioned from {} to {}",
                                    event.getCircuitBreakerName(),
                                    event.getStateTransition().getFromState(),
                                    event.getStateTransition().getToState())
                        )
                        .onError(event -> 
                            log.error("Circuit Breaker '{}' recorded error: {}",
                                    event.getCircuitBreakerName(),
                                    event.getThrowable().getMessage())
                        )
                        .onSuccess(event -> 
                            log.debug("Circuit Breaker '{}' recorded success", event.getCircuitBreakerName())
                        )
                        .onCallNotPermitted(event ->
                            log.warn("Circuit Breaker '{}' rejected call (circuit is OPEN)", 
                                    event.getCircuitBreakerName())
                        );
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemoveEvent) {
                log.info("Circuit Breaker '{}' removed", entryRemoveEvent.getRemovedEntry().getName());
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
                log.info("Circuit Breaker '{}' replaced", entryReplacedEvent.getNewEntry().getName());
            }
        };
    }

    @Bean
    public RegistryEventConsumer<Retry> retryEventConsumer() {
        return new RegistryEventConsumer<Retry>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<Retry> entryAddedEvent) {
                Retry retry = entryAddedEvent.getAddedEntry();
                retry.getEventPublisher()
                        .onRetry(event -> 
                            log.warn("Retry '{}' attempt #{} - waiting {}ms before next attempt. Error: {}",
                                    event.getName(),
                                    event.getNumberOfRetryAttempts(),
                                    event.getWaitInterval().toMillis(),
                                    event.getLastThrowable().getMessage())
                        )
                        .onError(event -> 
                            log.error("Retry '{}' exhausted all {} attempts. Final error: {}",
                                    event.getName(),
                                    event.getNumberOfRetryAttempts(),
                                    event.getLastThrowable().getMessage())
                        )
                        .onSuccess(event -> 
                            log.debug("Retry '{}' succeeded after {} attempt(s)", 
                                    event.getName(), event.getNumberOfRetryAttempts())
                        );
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<Retry> entryRemoveEvent) {
                log.info("Retry '{}' removed", entryRemoveEvent.getRemovedEntry().getName());
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<Retry> entryReplacedEvent) {
                log.info("Retry '{}' replaced", entryReplacedEvent.getNewEntry().getName());
            }
        };
    }
}
