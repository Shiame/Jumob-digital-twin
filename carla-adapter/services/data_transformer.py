"""
CARLA Adapter — Data Transformer
==================================
Transforms raw CARLA actor data into structured CarlaStateEvent DTOs.
Handles unit conversions (m/s → km/h) and data normalization.
"""

import math
import logging
from typing import List

from models.events import VehicleState, CollisionEvent, CarlaPayload, CarlaStateEvent

logger = logging.getLogger("carla-adapter.transformer")


def compute_speed_kmh(vx: float, vy: float, vz: float) -> float:
    """Convert velocity components (m/s) to speed (km/h)."""
    speed_ms = math.sqrt(vx ** 2 + vy ** 2 + vz ** 2)
    return round(speed_ms * 3.6, 2)


def transform_actor(actor, carla_map=None) -> VehicleState:
    """
    Extract state from a real CARLA vehicle actor.
    
    Args:
        actor: carla.Vehicle object with get_transform() and get_velocity()
        carla_map: carla.Map object for geographic coordinate conversion (optional)
    
    Returns:
        VehicleState DTO with position, rotation, velocity, speed, and geo-coordinates.
    """
    transform = actor.get_transform()
    velocity = actor.get_velocity()

    vx = round(velocity.x, 4)
    vy = round(velocity.y, 4)
    vz = round(velocity.z, 4)

    # Convert CARLA local coordinates to geographic (WGS84) coordinates
    # 🎯 DIGITAL TWIN ALIGNMENT OFFSET 🎯
    # We ADD the offset so that CARLA's raw coordinates are shifted to match GAMA's absolute GPS grid.
    from config import settings
    latitude = 0.0
    longitude = 0.0
    if carla_map is not None:
        try:
            geo = carla_map.transform_to_geolocation(transform.location)
            
            # Apply abs() in case CARLA's mapping flips hemispheres, and ADD the offset
            # so the orchestrator sees the exact GAMA coordinates.
            latitude = round(abs(geo.latitude) + settings.GAMA_VIS_LAT_OFFSET, 8)
            longitude = round(abs(geo.longitude) + settings.GAMA_VIS_LON_OFFSET, 8)
        except Exception as e:
            logger.warning("Failed to convert to geolocation for actor %d: %s", actor.id, e)

    # Extract role_name (identifies ego vehicle as "hero")
    role_name = actor.attributes.get("role_name", "")

    return VehicleState(
        id=actor.id,
        type=actor.type_id,
        x=round(transform.location.x, 4),
        y=round(transform.location.y, 4),
        z=round(transform.location.z, 4),
        latitude=latitude,
        longitude=longitude,
        pitch=round(transform.rotation.pitch, 2),
        yaw=round(transform.rotation.yaw, 2),
        roll=round(transform.rotation.roll, 2),
        vx=vx,
        vy=vy,
        vz=vz,
        speed_kmh=compute_speed_kmh(vx, vy, vz),
        role_name=role_name,
    )


def build_event(
    tick_number: int,
    vehicles: List[VehicleState],
    map_name: str = "unknown",
    collision_events: List[CollisionEvent] = None,
) -> CarlaStateEvent:
    """
    Build a complete CarlaStateEvent from vehicle states and collision events.
    """
    payload = CarlaPayload(
        tick_number=tick_number,
        map_name=map_name,
        num_vehicles=len(vehicles),
        vehicles=vehicles,
        events=collision_events or [],
    )

    event = CarlaStateEvent(payload=payload)
    logger.debug(
        "Built event %s | tick=%d | vehicles=%d | collisions=%d",
        event.eventId, tick_number, len(vehicles), len(payload.events),
    )
    return event
