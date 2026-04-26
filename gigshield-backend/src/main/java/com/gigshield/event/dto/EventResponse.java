// com/gigshield/event/dto/EventResponse.java
package com.gigshield.event.dto;

import com.gigshield.event.enums.EventStatus;
import com.gigshield.event.enums.EventType;
import lombok.*;

import java.time.LocalDateTime;

@Data @Builder
public class EventResponse {
    private Long          id;
    private EventType     eventType;
    private String        city;
    private Double        metricValue;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private EventStatus   status;
    private String        sourceSystem;
    private String        externalReference;
}