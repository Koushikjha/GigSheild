// com/gigshield/event/kafka/DisruptionEventProducer.java
package com.gigshield.event.kafka;

import com.gigshield.event.entity.DisruptionEvent;
import com.gigshield.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DisruptionEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes the just-persisted DisruptionEvent so downstream consumers
     * (claims automation, and — via that — payment automation) can react
     * asynchronously. Keyed by city so all events for the same city stay
     * ordered on the same partition.
     */
    public void publish(DisruptionEvent event) {
        DisruptionEventMessage message = DisruptionEventMessage.builder()
                .eventId(event.getId())
                .eventType(event.getEventType())
                .city(event.getCity())
                .metricValue(event.getMetricValue())
                .sourceSystem(event.getSourceSystem())
                .externalReference(event.getExternalReference())
                .occurredAt(event.getStartTime())
                .build();

        kafkaTemplate.send(KafkaTopics.DISRUPTION_EVENT_CREATED, event.getCity(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish disruption event id={} city={}: {}",
                                event.getId(), event.getCity(), ex.getMessage());
                    } else {
                        log.info("Published disruption event id={} type={} city={} to {}",
                                event.getId(), event.getEventType(), event.getCity(),
                                KafkaTopics.DISRUPTION_EVENT_CREATED);
                    }
                });
    }
}
