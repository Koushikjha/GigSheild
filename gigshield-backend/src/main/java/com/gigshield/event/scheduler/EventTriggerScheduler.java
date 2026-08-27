// com/gigshield/event/scheduler/EventTriggerScheduler.java
package com.gigshield.event.scheduler;

import com.gigshield.event.dto.CreateEventRequest;
import com.gigshield.event.enums.EventStatus;
import com.gigshield.event.enums.EventType;
import com.gigshield.event.repository.DisruptionEventRepository;
import com.gigshield.event.service.EventService;
import com.gigshield.integration.MlServiceClient;
import com.gigshield.integration.dto.TriggerCheckResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventTriggerScheduler {

    private final MlServiceClient           mlServiceClient;
    private final EventService              eventService;
    private final DisruptionEventRepository eventRepo;

    @Value("${gigshield.monitored-cities:" +
            "Delhi,Mumbai,Bengaluru,Chennai,Hyderabad,Kolkata}")
    private String monitoredCitiesRaw;

    @Scheduled(fixedRateString =
            "${gigshield.scheduler.poll-interval-ms:900000}")
    public void pollAndFireTriggers() {
        List<String> cities = Arrays.stream(monitoredCitiesRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        for (String city : cities) {
            processCity(city);
        }
    }

    private void processCity(String city) {
        try {
            TriggerCheckResponse response =
                    mlServiceClient.getTriggerCheck(city);

            if (response.getActiveTriggers() == null
                    || response.getActiveTriggers().isEmpty()) {
                log.debug("No active triggers for city={}", city);
                return;
            }

            for (String triggerName : response.getActiveTriggers()) {
                EventType eventType;
                try {
                    eventType = EventType.valueOf(triggerName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown trigger type from ML: {} for city={}",
                            triggerName, city);
                    continue;
                }

                if (isEventActive(city, eventType)) {
                    log.debug("Event already active city={} type={}",
                            city, eventType);
                    continue;
                }

                Double metric = response.getMetricValues() != null
                        ? response.getMetricValues().get(triggerName)
                        : null;

                createEvent(city, eventType, metric);
            }
        } catch (Exception e) {
            log.error("Trigger processing failed city={}: {}",
                    city, e.getMessage());
        }
    }

    private boolean isEventActive(String city, EventType type) {
        return eventRepo.existsByCityAndEventTypeAndStatus(
                city, type, EventStatus.ACTIVE);
    }

    private void createEvent(String city, EventType type, Double metric) {
        CreateEventRequest req = new CreateEventRequest();
        req.setEventType(type);
        req.setCity(city);
        req.setMetricValue(metric);
        req.setSourceSystem("ML_SIDECAR");
        eventService.createEvent(req);
        log.info("Trigger fired city={} type={} metric={} source=ML_SIDECAR",
                city, type, metric);
    }
}