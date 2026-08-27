// com/gigshield/event/dto/CreateEventRequest.java
package com.gigshield.event.dto;

import com.gigshield.event.enums.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateEventRequest {
    @NotNull
    private EventType eventType;

    @NotBlank
    private String city;

    private Double metricValue;

    private String sourceSystem;

    private String externalReference;
}