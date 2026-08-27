// com/gigshield/event/kafka/DisruptionEventMessage.java
package com.gigshield.event.kafka;

import com.gigshield.event.enums.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kafka payload published to {@code gigshield.events.disruption} whenever a
 * DisruptionEvent is created — by an admin, or by the ML-driven
 * {@code EventTriggerScheduler}. This is the entry point of the event-driven
 * pipeline: claims automation and, downstream, payment automation both react
 * to this message instead of being called synchronously.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisruptionEventMessage {
    private Long          eventId;
    private EventType     eventType;
    private String        city;
    private Double        metricValue;
    private String        sourceSystem;
    private String        externalReference;
    private LocalDateTime occurredAt;
}
