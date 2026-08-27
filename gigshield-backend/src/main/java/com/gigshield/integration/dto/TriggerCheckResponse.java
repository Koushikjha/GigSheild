// com/gigshield/integration/dto/TriggerCheckResponse.java
package com.gigshield.integration.dto;

import lombok.Data;
import java.util.List;

@Data
public class TriggerCheckResponse {

    /** City this result applies to */
    private String city;

    /**
     * List of currently active trigger types for this city.
     * Values match EventType enum: RAIN, AQI, CURFEW, TRAFFIC, WAR
     */
    private List<String> activeTriggers;

    /**
     * Raw metric values keyed by trigger type.
     * e.g. {"RAIN": 42.5, "AQI": 310.0}
     * Absent for boolean triggers like CURFEW, WAR.
     */
    private java.util.Map<String, Double> metricValues;
}