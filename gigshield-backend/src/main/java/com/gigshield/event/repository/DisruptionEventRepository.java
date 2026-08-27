// com/gigshield/event/repository/DisruptionEventRepository.java
package com.gigshield.event.repository;

import com.gigshield.event.entity.DisruptionEvent;
import com.gigshield.event.enums.EventStatus;
import com.gigshield.event.enums.EventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface DisruptionEventRepository extends JpaRepository<DisruptionEvent, Long> {

    List<DisruptionEvent> findByCityAndStatusOrderByStartTimeDesc(
            String city, EventStatus status);

    List<DisruptionEvent> findByCityAndEventTypeAndStatusAndStartTimeAfter(
            String city, EventType type, EventStatus status, LocalDateTime since);

    boolean existsByCityAndEventTypeAndStatus(
            String city, EventType type, EventStatus status);
}