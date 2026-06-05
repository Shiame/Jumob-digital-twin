"""
CARLA Adapter — Data Models (DTOs)
===================================
Pydantic models for structured CARLA events published to Kafka.
Mirrors the GamaStateEvent contract from the GAMA Adapter.
"""

import uuid
import time
from typing import List, Optional
from pydantic import BaseModel, Field


class VehicleState(BaseModel):
    """State of a single vehicle actor in the CARLA simulation."""

    id: int = Field(description="CARLA actor ID")
    type: str = Field(default="vehicle.unknown", description="CARLA vehicle blueprint ID (e.g. vehicle.tesla.model3)")
    
    # Position (world coordinates, meters)
    x: float = Field(default=0.0, description="X position (meters)")
    y: float = Field(default=0.0, description="Y position (meters)")
    z: float = Field(default=0.0, description="Z position (meters)")

    # Geographic coordinates (WGS84) — for co-simulation geo-proximity rules
    latitude: float = Field(default=0.0, description="GPS latitude (WGS84)")
    longitude: float = Field(default=0.0, description="GPS longitude (WGS84)")
    
    # Rotation (degrees)
    pitch: float = Field(default=0.0, description="Pitch rotation (degrees)")
    yaw: float = Field(default=0.0, description="Yaw rotation (degrees)")
    roll: float = Field(default=0.0, description="Roll rotation (degrees)")
    
    # Velocity (m/s)
    vx: float = Field(default=0.0, description="Velocity X (m/s)")
    vy: float = Field(default=0.0, description="Velocity Y (m/s)")
    vz: float = Field(default=0.0, description="Velocity Z (m/s)")
    
    # Derived
    speed_kmh: float = Field(default=0.0, description="Current speed (km/h)")

    # Role — identifies the ego vehicle ("hero") vs NPC traffic
    role_name: str = Field(default="", description="CARLA role_name attribute (hero = ego vehicle)")


class CollisionEvent(BaseModel):
    """A collision detected by the CARLA collision sensor."""

    type: str = Field(default="COLLISION", description="Event type")
    position: dict = Field(default_factory=lambda: {"x": 0.0, "y": 0.0}, description="Collision position")
    latitude: float = Field(default=0.0, description="GPS latitude (WGS84)")
    longitude: float = Field(default=0.0, description="GPS longitude (WGS84)")
    otherActorType: str = Field(default="unknown", description="Type of other actor involved")
    severity: str = Field(default="medium", description="Collision severity (low/medium/high)")


class CarlaPayload(BaseModel):
    """Payload containing simulation state for a given tick."""

    tick_number: int = Field(description="CARLA world snapshot frame number")
    map_name: str = Field(default="unknown", description="Name of the loaded CARLA map")
    num_vehicles: int = Field(default=0, description="Total number of vehicles in the scene")
    vehicles: List[VehicleState] = Field(default_factory=list, description="List of vehicle states")
    events: List[CollisionEvent] = Field(default_factory=list, description="Sensor events (collisions)")


class CarlaStateEvent(BaseModel):
    """
    Standard event wrapper for all CARLA → Kafka messages.
    Mirrors the GamaStateEvent contract for interoperability.
    """

    eventId: str = Field(
        default_factory=lambda: f"carla-{uuid.uuid4().hex[:8]}",
        description="Unique event identifier"
    )
    eventType: str = Field(default="TICK_COMPLETED", description="Type of event")
    source: str = Field(default="CARLA", description="Source simulator")
    timestamp: int = Field(
        default_factory=lambda: int(time.time() * 1000),
        description="Event timestamp (milliseconds since epoch)"
    )
    payload: CarlaPayload = Field(description="Simulation state payload")


class CarlaCommandMessage(BaseModel):
    """Command received from the orchestrator via carla-commands topic."""

    commandId: str = Field(default="", description="Unique command identifier")
    commandType: str = Field(description="SET_SPEED_LIMIT | BLOCK_ZONE_ENTRY | CHANGE_TRAFFIC_LIGHT | SPAWN_NPC")
    maxSpeedKmh: Optional[int] = Field(default=None)
    zoneId: Optional[str] = Field(default=None)
    trafficLightId: Optional[str] = Field(default=None)
    newState: Optional[str] = Field(default=None)
    agentId: Optional[str] = Field(default=None)
    x: Optional[float] = Field(default=None)
    y: Optional[float] = Field(default=None)
    agentType: Optional[str] = Field(default=None)
    triggeredBy: Optional[str] = Field(default=None)
