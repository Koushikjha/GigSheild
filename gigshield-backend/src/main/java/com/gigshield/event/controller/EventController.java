// com/gigshield/event/controller/EventController.java
package com.gigshield.event.controller;

import com.gigshield.event.dto.CreateEventRequest;
import com.gigshield.event.dto.EventResponse;
import com.gigshield.event.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.createEvent(request));
    }

    @GetMapping("/city/{city}/active")
    public ResponseEntity<List<EventResponse>> getActiveEvents(@PathVariable String city) {
        return ResponseEntity.ok(eventService.getActiveEventsForCity(city));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EventResponse> resolveEvent(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.resolveEvent(id));
    }
}