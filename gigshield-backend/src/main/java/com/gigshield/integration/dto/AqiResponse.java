// com/gigshield/integration/dto/AqiResponse.java
package com.gigshield.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AqiResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("data")
    private AqiData data;

    public boolean isOk() {
        return "ok".equalsIgnoreCase(status);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AqiData {

        @JsonProperty("aqi")
        private Object aqi;     // WAQI returns "-" (String) when data unavailable

        @JsonProperty("idx")
        private Integer idx;

        @JsonProperty("city")
        private CityInfo city;  // object, NOT a String

        @JsonProperty("time")
        private TimeInfo time;

        /**
         * Safe int conversion.
         * WAQI returns "-" as aqi value when station data is unavailable.
         */
        public int getAqiSafe() {
            if (aqi == null) return 0;
            if (aqi instanceof Integer i) return i;
            if (aqi instanceof Number n) return n.intValue();
            try {
                return Integer.parseInt(aqi.toString().trim());
            } catch (NumberFormatException e) {
                return 0;   // "-" or any non-numeric → treat as no data
            }
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CityInfo {
        @JsonProperty("name")
        private String name;

        @JsonProperty("geo")
        private double[] geo;   // [lat, lon]
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TimeInfo {
        @JsonProperty("s")
        private String timestamp;

        @JsonProperty("tz")
        private String timezone;
    }
}