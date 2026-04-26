// com/gigshield/event/service/EventService.java
package com.gigshield.event.service;

import com.gigshield.event.dto.CreateEventRequest;
import com.gigshield.event.dto.EventResponse;
import com.gigshield.event.entity.DisruptionEvent;
import com.gigshield.event.enums.EventStatus;
import com.gigshield.event.enums.EventType;
import com.gigshield.event.repository.DisruptionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final DisruptionEventRepository eventRepository;

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        DisruptionEvent event = DisruptionEvent.builder()
                .eventType(request.getEventType())
                .city(request.getCity())
                .metricValue(request.getMetricValue())
                .startTime(LocalDateTime.now())
                .sourceSystem(request.getSourceSystem())
                .externalReference(request.getExternalReference())
                .build();

        event = eventRepository.save(event);
        log.info("Disruption event created: type={} city={} id={}",
                event.getEventType(), event.getCity(), event.getId());
        return toResponse(event);
    }

    public List<EventResponse> getActiveEventsForCity(String city) {
        return eventRepository
                .findByCityAndStatusOrderByStartTimeDesc(
                        city, EventStatus.ACTIVE)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Single source of truth for parametric trigger check.
     * ML sidecar owns the threshold decision — Spring Boot just checks
     * whether an ACTIVE event record exists for this city + type.
     * No threshold comparison happens here anymore.
     */
    public boolean isTriggerActive(String city, EventType type) {
        return eventRepository
                .existsByCityAndEventTypeAndStatus(
                        city, type, EventStatus.ACTIVE);
    }

    @Transactional
    public EventResponse resolveEvent(Long eventId) {
        DisruptionEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Event not found: " + eventId));
        event.setStatus(EventStatus.RESOLVED);
        event.setEndTime(LocalDateTime.now());
        return toResponse(eventRepository.save(event));
    }

    private EventResponse toResponse(DisruptionEvent e) {
        return EventResponse.builder()
                .id(e.getId())
                .eventType(e.getEventType())
                .city(e.getCity())
                .metricValue(e.getMetricValue())
                .startTime(e.getStartTime())
                .endTime(e.getEndTime())
                .status(e.getStatus())
                .sourceSystem(e.getSourceSystem())
                .externalReference(e.getExternalReference())
                .build();
    }
}