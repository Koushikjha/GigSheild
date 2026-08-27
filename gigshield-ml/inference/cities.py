"""
Centroid coordinates for the cities GigShield monitors by default
(``gigshield.monitored-cities`` in the Spring Boot backend). Used:

  * as the reference point for GET /trigger-check, which only receives a
    city name (no lat/long) from the Java scheduler, and
  * to compute a `gps_distance` proxy feature for /fraud-check, comparing a
    worker's registered coordinates against their claimed city.

Extend this table if the backend's monitored-cities list grows — a city
missing here still works, it just falls back to a heuristic (no live
weather) rather than a hard error.
"""

from __future__ import annotations

CITY_COORDINATES: dict[str, tuple[float, float]] = {
    "delhi": (28.6139, 77.2090),
    "mumbai": (19.0760, 72.8777),
    "bengaluru": (12.9716, 77.5946),
    "bangalore": (12.9716, 77.5946),
    "chennai": (13.0827, 80.2707),
    "hyderabad": (17.3850, 78.4867),
    "kolkata": (22.5726, 88.3639),
}


def lookup_city(city: str | None) -> tuple[float, float] | None:
    """Case/whitespace-insensitive centroid lookup. Returns None if unknown."""
    if not city:
        return None
    return CITY_COORDINATES.get(city.strip().lower())
