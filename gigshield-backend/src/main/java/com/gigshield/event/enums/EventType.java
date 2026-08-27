// com/gigshield/event/enums/EventType.java
package com.gigshield.event.enums;

/**
 * RAIN/AQI/CURFEW/TRAFFIC/WAR are city-wide macro disruptions — detected
 * automatically (ML trigger-check) or admin-entered, and fanned out to
 * every covered worker in the city by the Kafka claims-automation pipeline
 * ({@code DisruptionEventListener}).
 *
 * ORDER_CANCELLED is different in kind: it's a single worker's single
 * missed order, not a city-wide event. It is deliberately NEVER created by
 * the automated pipeline or by admins ({@code EventService#createEvent}
 * rejects it) — only a worker can report one, via
 * {@code ClaimService#reportCancelledOrder}. Automating it would mean every
 * ordinary, disruption-unrelated cancellation (a restaurant closed, a
 * customer changed their mind) silently ran a fraud check and could rack up
 * strikes against a worker who did nothing wrong.
 */
public enum EventType { RAIN, AQI, CURFEW, TRAFFIC, WAR, ORDER_CANCELLED }