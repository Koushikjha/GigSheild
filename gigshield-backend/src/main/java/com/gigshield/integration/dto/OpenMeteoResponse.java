// com/gigshield/integration/dto/OpenMeteoResponse.java
package com.gigshield.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenMeteoResponse {

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("timezone")
    private String timezone;

    @JsonProperty("current")
    private Current current;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Current {

        @JsonProperty("time")
        private String time;

        @JsonProperty("rain")
        private Double rain;              // mm — rain only

        @JsonProperty("precipitation")
        private Double precipitation;    // mm — rain + showers + snow
    }
}