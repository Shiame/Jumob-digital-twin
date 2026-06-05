"""
CARLA Adapter — CARLA Connector Service
=========================================
Handles connection to the CARLA simulator and extraction of vehicle actor data.
Includes a MockCarlaConnector for development/testing without a GPU.
Supports command handlers for orchestrator commands (speed limit, traffic lights, etc.)

Road-aware SET_SPEED_LIMIT:
  The orchestrator sends targetCarlaRoadIds along with the speed command.
  The connector checks if the ego vehicle is on one of those roads before applying.
"""

import time
import math
import random
import logging
import threading
from abc import ABC, abstractmethod
from typing import List, Optional

from models.events import VehicleState, CollisionEvent
from config import settings

logger = logging.getLogger("carla-adapter.connector")


# ═══════════════════════════════════════════════════════════════
#  Abstract Base
# ═══════════════════════════════════════════════════════════════

class BaseCarlaConnector(ABC):
    """Abstract interface for CARLA connectors."""

    @abstractmethod
    def connect(self) -> bool:
        """Connect to the simulator. Returns True on success."""
        ...

    @abstractmethod
    def get_vehicles(self) -> List[VehicleState]:
        """Extract current vehicle states."""
        ...

    @abstractmethod
    def get_tick_number(self) -> int:
        """Get the current simulation frame/tick number."""
        ...

    @abstractmethod
    def get_map_name(self) -> str:
        """Get the name of the currently loaded map."""
        ...

    @abstractmethod
    def is_connected(self) -> bool:
        """Check if the connector is currently connected."""
        ...

    @abstractmethod
    def get_collision_events(self) -> List[CollisionEvent]:
        """Get pending collision events since last call."""
        ...

    # --- Accessors for visualization / cross-service usage ---

    @property
    def world(self):
        """Return the CARLA world object (None in mock mode)."""
        return None

    @property
    def carla_map(self):
        """Return the CARLA map object (None in mock mode)."""
        return None

    # --- Command handlers (called by KafkaCommandConsumer) ---

    def set_speed_limit(self, speed_kmh: int):
        """Apply a speed limit to the ego vehicle (legacy, no road check)."""
        logger.info("set_speed_limit(%d) — not implemented in this connector", speed_kmh)

    def set_speed_limit_on_roads(self, speed_kmh: int, target_road_ids: list,
                                  source_gama_road: str = "?", reason: str = "UNKNOWN"):
        """
        Apply a speed limit to the ego vehicle ONLY if it is on one of the target roads.
        This is the road-aware version used by the S1_PEDESTRIAN_DENSITY scenario.
        """
        logger.info("set_speed_limit_on_roads(%d, %s) — not implemented in this connector",
                     speed_kmh, target_road_ids)

    def change_traffic_light(self, tl_id: str, state: str):
        """Change a traffic light state."""
        logger.info("change_traffic_light(%s, %s) — not implemented", tl_id, state)

    def block_zone_entry(self, zone_id: str):
        """Block vehicle from entering a zone."""
        logger.info("block_zone_entry(%s) — not implemented", zone_id)

    def spawn_npc(self, x: float, y: float, agent_type: str):
        """Spawn a NPC at the given position."""
        logger.info("spawn_npc(%s, %.1f, %.1f) — not implemented", agent_type, x, y)


# ═══════════════════════════════════════════════════════════════
#  Real CARLA Connector (requires carla package + GPU)
# ═══════════════════════════════════════════════════════════════

class CarlaConnectorService(BaseCarlaConnector):
    """Connects to a real CARLA simulator via the Python API."""

    def __init__(self):
        self._client = None
        self._world = None
        self._map = None
        self._connected = False
        self._map_name = "unknown"
        self._collision_events: List[CollisionEvent] = []
        self._collision_sensor = None
        self._ego_vehicle = None

    @property
    def world(self):
        return self._world

    @property
    def carla_map(self):
        return self._map

    def connect(self) -> bool:
        try:
            import carla
            logger.info(
                "Connecting to CARLA at %s:%d (timeout: %.1fs)...",
                settings.CARLA_HOST,
                settings.CARLA_PORT,
                settings.CARLA_TIMEOUT,
            )
            self._client = carla.Client(settings.CARLA_HOST, settings.CARLA_PORT)
            self._client.set_timeout(settings.CARLA_TIMEOUT)
            self._world = self._client.get_world()
            self._map = self._world.get_map()
            self._map_name = self._map.name.split("/")[-1]
            self._connected = True
            logger.info("Connected to CARLA! Map: %s", self._map_name)

            # Try to find the ego vehicle (role_name=hero)
            self._find_ego_vehicle()
            return True
        except Exception as e:
            self._connected = False
            logger.error("Failed to connect to CARLA: %s", e)
            return False

    def _find_ego_vehicle(self):
        """Find the ego vehicle with role_name='hero'."""
        actors = self._world.get_actors().filter("vehicle.*")

        # Priority: vehicle with role_name='hero'
        for actor in actors:
            if actor.attributes.get("role_name") == "hero":
                self._ego_vehicle = actor
                logger.info("✅ Found ego vehicle (hero): %s (id=%d)", actor.type_id, actor.id)
                self._attach_collision_sensor(actor)
                return

        # Fallback: first vehicle found
        if len(actors) > 0:
            self._ego_vehicle = actors[0]
            logger.warning("⚠️ No hero vehicle found — using first vehicle: %s (id=%d)",
                           actors[0].type_id, actors[0].id)
            self._attach_collision_sensor(actors[0])
            return

        logger.warning("❌ No vehicles found in the world — ego vehicle not set")

    def _attach_collision_sensor(self, vehicle):
        """Attach a collision sensor to the ego vehicle."""
        import carla
        bp = self._world.get_blueprint_library().find("sensor.other.collision")
        self._collision_sensor = self._world.spawn_actor(bp, carla.Transform(), attach_to=vehicle)
        self._collision_sensor.listen(self._on_collision)
        logger.info("🔧 Collision sensor attached to ego vehicle")

    def _on_collision(self, event):
        """Callback for collision events from the sensor."""
        other = event.other_actor
        other_type = other.type_id if other else "unknown"

        # Ignorer les collisions avec le sol ou le décor (static.unknown, static.road, etc.) pour éviter le spam
        if "static" in other_type:
            return

        loc = event.transform.location
        impulse = event.normal_impulse
        intensity = math.sqrt(impulse.x**2 + impulse.y**2 + impulse.z**2)

        severity = "low" if intensity < 100 else ("high" if intensity > 500 else "medium")

        # 🎯 DIGITAL TWIN ALIGNMENT OFFSET 🎯
        # We apply the EXACT same geographic offset logic here as we did for vehicles/pedestrians!
        # This guarantees generalization to any collision on the map.
        from config import settings
        lat = 0.0
        lon = 0.0
        if self._map is not None:
            try:
                geo = self._map.transform_to_geolocation(loc)
                lat = round(abs(geo.latitude) + settings.GAMA_VIS_LAT_OFFSET, 8)
                lon = round(abs(geo.longitude) + settings.GAMA_VIS_LON_OFFSET, 8)
            except Exception as e:
                logger.warning("Failed to map collision geo: %s", e)

        collision = CollisionEvent(
            type="COLLISION",
            position={"x": round(loc.x, 2), "y": round(loc.y, 2)},
            latitude=lat,
            longitude=lon,
            otherActorType=other.type_id if other else "unknown",
            severity=severity,
        )
        self._collision_events.append(collision)
        logger.error("🚨 COLLISION with %s at (%.1f, %.1f) — severity: %s",
                      other.type_id if other else "?", loc.x, loc.y, severity)

    def _get_vehicle_road_id(self, vehicle) -> Optional[int]:
        """Get the current CARLA road_id where the vehicle is located."""
        try:
            location = vehicle.get_location()
            waypoint = self._map.get_waypoint(location, project_to_road=True)
            if waypoint:
                return waypoint.road_id
        except Exception as e:
            logger.error("Failed to get road_id for vehicle %d: %s", vehicle.id, e)
        return None

    def get_vehicles(self) -> List[VehicleState]:
        if not self._connected: return []
        
        # Si on n'a pas encore trouvé le véhicule HERO, on le cherche
        if self._ego_vehicle is None:
            self._find_ego_vehicle()

        from services.data_transformer import transform_actor
        actors = self._world.get_actors().filter("vehicle.*")
        return [transform_actor(actor, carla_map=self._map) for actor in actors]

    def get_collision_events(self) -> List[CollisionEvent]:
        events = list(self._collision_events)
        self._collision_events.clear()
        return events

    def get_tick_number(self) -> int:
        if not self._world:
            return -1
        return self._world.get_snapshot().frame

    def get_map_name(self) -> str:
        return self._map_name

    def is_connected(self) -> bool:
        return self._connected

    # --- Command Handlers ---

    def set_speed_limit_ego(self, speed_kmh: int, reason: str = "UNKNOWN"):
        """
        Apply a speed limit directly to the ego vehicle (geo-proximity mode).
        No road check — the orchestrator already validated proximity.
        """
        import carla as carla_module

        if self._ego_vehicle is None:
            self._find_ego_vehicle()
        if self._ego_vehicle is None:
            logger.warning("No ego vehicle found — cannot apply SET_SPEED_LIMIT (reason=%s)", reason)
            return

        # Apply via Traffic Manager (keep autopilot, just slow down)
        try:
            tm = self._client.get_trafficmanager()
            # Calculate precise reduction: default city speed is 30 km/h
            reduction_pct = max(0.0, min(100.0, (30.0 - speed_kmh) / 30.0 * 100.0))
            tm.vehicle_percentage_speed_difference(self._ego_vehicle, reduction_pct)
            # Throttle logging: only log every 30 seconds
            import time as _time
            now = _time.time()
            if not hasattr(self, '_last_speed_log') or now - self._last_speed_log > 30:
                self._last_speed_log = now
                logger.info("GEO-PROXIMITY: speed=%d km/h, reduction=%.0f%%, reason=%s",
                             speed_kmh, reduction_pct, reason)
        except Exception as e:
            logger.error("Failed to set TrafficManager speed: %s", e)

    def set_speed_limit(self, speed_kmh: int):
        """Limit the ego vehicle speed via Traffic Manager (legacy, no road check)."""
        if not self._ego_vehicle or not self._client:
            logger.warning("No ego vehicle — cannot set speed limit")
            return
        try:
            tm = self._client.get_trafficmanager()
            # percentage_speed_difference: negative means faster, positive means slower
            # We want to cap at speed_kmh, so we compute the difference vs default
            speed_limit = self._ego_vehicle.get_speed_limit()  # m/s on the current road
            if speed_limit > 0:
                desired_ratio = (speed_kmh / 3.6) / speed_limit
                diff = (1.0 - desired_ratio) * 100
                tm.vehicle_percentage_speed_difference(self._ego_vehicle, diff)
            logger.info("✅ Speed limit set to %d km/h on ego vehicle", speed_kmh)
        except Exception as e:
            logger.error("Failed to set speed limit: %s", e)

    def set_speed_limit_on_roads(self, speed_kmh: int, target_road_ids: list,
                                  source_gama_road: str = "?", reason: str = "UNKNOWN"):
        """
        Road-aware SET_SPEED_LIMIT for the S1_PEDESTRIAN_DENSITY scenario.

        Steps:
          1. Find the ego vehicle (or refresh if not found)
          2. Get the ego vehicle's current CARLA road_id
          3. Check if that road_id is in targetCarlaRoadIds
          4. If yes → apply speed reduction via TrafficManager + visible braking
          5. If no → log and skip
        """
        import carla as carla_module

        # Refresh ego vehicle if needed
        if self._ego_vehicle is None:
            self._find_ego_vehicle()

        if self._ego_vehicle is None:
            logger.warning("❌ No ego vehicle found — cannot apply SET_SPEED_LIMIT "
                           "(gamaRoad=%s, carlaRoads=%s)", source_gama_road, target_road_ids)
            return

        # Get current road_id
        current_road_id = self._get_vehicle_road_id(self._ego_vehicle)
        logger.info("🚗 Ego vehicle %d is currently on road_id=%s",
                     self._ego_vehicle.id, current_road_id)
        logger.info("🗺️  Target CARLA road_ids: %s (from GAMA road %s)",
                     target_road_ids, source_gama_road)

        if current_road_id is None:
            logger.warning("⚠️ Could not determine ego vehicle's road_id — skipping command")
            return

        # Check if vehicle is on one of the target roads
        if current_road_id in target_road_ids:
            logger.info("✅ Ego vehicle IS on target road (road_id=%d) → Applying speed reduction to %d km/h (%s)",
                         current_road_id, speed_kmh, reason)

            # --- Apply via Traffic Manager ---
            try:
                tm = self._client.get_trafficmanager()
                if speed_kmh <= 20:
                    # Strong slowdown: 80%
                    tm.vehicle_percentage_speed_difference(self._ego_vehicle, 80)
                    logger.info("🐢 TrafficManager: 80%% speed reduction (target ≤20 km/h)")
                else:
                    # Medium slowdown: 60%
                    tm.vehicle_percentage_speed_difference(self._ego_vehicle, 60)
                    logger.info("🐢 TrafficManager: 60%% speed reduction (target ~30 km/h)")
            except Exception as e:
                logger.error("Failed to set TrafficManager speed: %s", e)

            # --- Apply visible braking for demo ---
            try:
                # Short brake to make the slowdown visually obvious
                self._ego_vehicle.apply_control(
                    carla_module.VehicleControl(throttle=0.0, brake=0.5)
                )
                logger.info("🛑 Braking applied (brake=0.5) for visible deceleration")

                # After 1 second, release brake and apply gentle throttle
                def release_brake():
                    try:
                        self._ego_vehicle.apply_control(
                            carla_module.VehicleControl(throttle=0.2, brake=0.0)
                        )
                        logger.info("🟢 Brake released, gentle throttle (0.2) applied")
                    except Exception as ex:
                        logger.error("Failed to release brake: %s", ex)

                timer = threading.Timer(1.0, release_brake)
                timer.daemon = True
                timer.start()

            except Exception as e:
                logger.error("Failed to apply braking control: %s", e)

        else:
            logger.info("ℹ️ Ego vehicle is NOT on target road (current=%d, targets=%s) "
                         "→ Ignoring SET_SPEED_LIMIT command (gamaRoad=%s)",
                         current_road_id, target_road_ids, source_gama_road)

    def change_traffic_light(self, tl_id: str, state: str):
        """Change a traffic light state in CARLA."""
        import carla
        state_map = {
            "GREEN": carla.TrafficLightState.Green,
            "RED": carla.TrafficLightState.Red,
            "YELLOW": carla.TrafficLightState.Yellow,
        }
        carla_state = state_map.get(state.upper(), carla.TrafficLightState.Green)
        try:
            traffic_lights = self._world.get_actors().filter("traffic.traffic_light")
            for tl in traffic_lights:
                tl.set_state(carla_state)
                tl.set_green_time(30.0)  # Keep green for 30s
            logger.info("✅ Traffic lights changed to %s", state)
        except Exception as e:
            logger.error("Failed to change traffic light: %s", e)


# ═══════════════════════════════════════════════════════════════
#  Mock CARLA Connector (for testing without GPU)
# ═══════════════════════════════════════════════════════════════

VEHICLE_BLUEPRINTS = [
    "vehicle.tesla.model3", "vehicle.audi.a2", "vehicle.bmw.grandtourer",
    "vehicle.citroen.c3", "vehicle.dodge.charger_2020", "vehicle.ford.mustang",
    "vehicle.mercedes.coupe_2020", "vehicle.toyota.prius",
]


class MockCarlaConnector(BaseCarlaConnector):
    """Generates realistic fake vehicle data for testing without CARLA."""

    def __init__(self, num_vehicles: int = 8):
        self._tick = 0
        self._connected = False
        self._num_vehicles = num_vehicles
        self._vehicles_state: List[dict] = []
        self._speed_limit: int = 80
        self._collision_events: List[CollisionEvent] = []
        # Mock: simulate the ego vehicle being on road 1476 by default
        self._mock_current_road_id = 1476

    def connect(self) -> bool:
        logger.info("🚗 MOCK MODE: Initializing %d virtual vehicles...", self._num_vehicles)
        # Center around Toulouse campus area for realistic geo-coordinates
        TOULOUSE_CENTER_LAT = 43.5647
        TOULOUSE_CENTER_LON = 1.4683
        self._vehicles_state = []
        for i in range(self._num_vehicles):
            # First vehicle is the ego/hero, rest are NPCs
            is_ego = (i == 0)
            self._vehicles_state.append({
                "id": 100 + i,
                "type": random.choice(VEHICLE_BLUEPRINTS),
                "x": random.uniform(-200, 200),
                "y": random.uniform(-200, 200),
                "z": 0.5,
                "yaw": random.uniform(0, 360),
                "speed_base": random.uniform(20, 80),
                # Geographic coordinates — small offsets around Toulouse campus
                "lat": TOULOUSE_CENTER_LAT + random.uniform(-0.002, 0.002),
                "lon": TOULOUSE_CENTER_LON + random.uniform(-0.002, 0.002),
                "role_name": "hero" if is_ego else "",
            })
        self._connected = True
        logger.info("🚗 MOCK MODE: Ready! %d vehicles initialized (vehicle 100 = hero).", self._num_vehicles)
        return True

    def get_vehicles(self) -> List[VehicleState]:
        vehicles = []
        for v in self._vehicles_state:
            yaw_rad = math.radians(v["yaw"])
            actual_speed = min(v["speed_base"], self._speed_limit)
            speed_ms = actual_speed / 3.6
            vx = speed_ms * math.cos(yaw_rad)
            vy = speed_ms * math.sin(yaw_rad)

            dt = settings.TICK_INTERVAL
            v["x"] += vx * dt
            v["y"] += vy * dt
            v["yaw"] += random.uniform(-2.0, 2.0)
            v["speed_base"] = max(5.0, v["speed_base"] + random.uniform(-1.0, 1.0))

            # Simulate random collision (1 in 500 chance per tick)
            if random.random() < 0.002:
                self._collision_events.append(CollisionEvent(
                    type="COLLISION",
                    position={"x": round(v["x"], 2), "y": round(v["y"], 2)},
                    otherActorType="walker.pedestrian.0001",
                    severity=random.choice(["low", "medium", "high"]),
                ))
                logger.warning("🚨 MOCK COLLISION at (%.1f, %.1f)", v["x"], v["y"])

            vehicles.append(VehicleState(
                id=v["id"], type=v["type"],
                x=round(v["x"], 4), y=round(v["y"], 4), z=v["z"],
                latitude=round(v["lat"], 8),
                longitude=round(v["lon"], 8),
                pitch=0.0, yaw=round(v["yaw"], 2), roll=0.0,
                vx=round(vx, 4), vy=round(vy, 4), vz=0.0,
                speed_kmh=round(actual_speed, 2),
                role_name=v.get("role_name", ""),
            ))
        return vehicles

    def get_collision_events(self) -> List[CollisionEvent]:
        events = list(self._collision_events)
        self._collision_events.clear()
        return events

    def get_tick_number(self) -> int:
        self._tick += 1
        return self._tick

    def get_map_name(self) -> str:
        return "MockTown_Toulouse"

    def is_connected(self) -> bool:
        return self._connected

    # --- Mock command handlers ---

    def set_speed_limit_ego(self, speed_kmh: int, reason: str = "UNKNOWN"):
        """Mock direct ego speed limit (geo-proximity mode)."""
        self._speed_limit = speed_kmh
        logger.info("✅ MOCK GEO-PROXIMITY: Speed limit set to %d km/h (reason=%s)", speed_kmh, reason)

    def set_speed_limit(self, speed_kmh: int):
        self._speed_limit = speed_kmh
        logger.info("🚗 MOCK: Speed limit set to %d km/h", speed_kmh)

    def set_speed_limit_on_roads(self, speed_kmh: int, target_road_ids: list,
                                  source_gama_road: str = "?", reason: str = "UNKNOWN"):
        """Mock road-aware speed limit — simulates the ego on road 1476."""
        logger.info("🚗 MOCK: SET_SPEED_LIMIT received — gamaRoad=%s, carlaRoads=%s, speed=%d, reason=%s",
                     source_gama_road, target_road_ids, speed_kmh, reason)
        logger.info("🚗 MOCK: Ego vehicle current road_id=%d", self._mock_current_road_id)

        if self._mock_current_road_id in target_road_ids:
            self._speed_limit = speed_kmh
            logger.info("✅ MOCK: Vehicle IS on target road → speed reduced to %d km/h", speed_kmh)
        else:
            logger.info("ℹ️ MOCK: Vehicle NOT on target road → command ignored")

    def change_traffic_light(self, tl_id: str, state: str):
        logger.info("🚗 MOCK: Traffic light %s → %s", tl_id, state)

    def block_zone_entry(self, zone_id: str):
        logger.info("🚗 MOCK: Zone '%s' blocked", zone_id)

    def spawn_npc(self, x: float, y: float, agent_type: str):
        logger.info("🚗 MOCK: Spawned %s at (%.1f, %.1f)", agent_type, x, y)


# ═══════════════════════════════════════════════════════════════
#  Factory
# ═══════════════════════════════════════════════════════════════

def create_connector() -> BaseCarlaConnector:
    """Factory: returns Mock or Real connector based on MOCK_MODE config."""
    if settings.MOCK_MODE:
        logger.info("MOCK_MODE is enabled — using MockCarlaConnector")
        return MockCarlaConnector(num_vehicles=settings.NUM_MOCK_VEHICLES)
    else:
        logger.info("MOCK_MODE is disabled — using real CarlaConnectorService")
        return CarlaConnectorService()
