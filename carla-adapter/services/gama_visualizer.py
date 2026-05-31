"""
CARLA Adapter — GAMA Agent Visualizer
========================================
Consumes GAMA state events from Kafka and visualizes GAMA agents inside the
CARLA 3D scene using debug markers (draw_string / draw_point).

This is the first version (V1): debug visualization only, no real actor spawning.
Later, markers will be replaced with actual CARLA walkers/vehicles.

Architecture:
  - A background Kafka consumer thread polls `gama-state` and stores the latest
    parsed agent list in a thread-safe buffer.
  - The main loop calls `draw_agents()` each tick, which reads the buffer and
    draws colored debug markers at the corresponding CARLA coordinates.

Coordinate conversion:
  GAMA agent.x = longitude, agent.y = latitude (EPSG:4326 / WGS84).
  We use carla.GeoLocation → carla_map.geolocation_to_transform() to convert,
  then snap to the nearest waypoint for correct Z placement.
"""

import json
import logging
import threading
from typing import List, Dict, Optional, Any

from confluent_kafka import Consumer, KafkaError

from config import settings

logger = logging.getLogger("carla-adapter.gama-visualizer")


# ═══════════════════════════════════════════════════════════════
#  Agent type → visual style mapping
# ═══════════════════════════════════════════════════════════════

AGENT_STYLES = {
    # type: (label, R, G, B)
    "pedestrian": ("PED",  148, 103, 189),   # purple
    "bike":       ("BIKE",  31, 119, 180),   # blue
    "car":        ("CAR",   44, 160,  44),   # green
    "bus":        ("BUS",  255, 165,   0),   # orange
}

DEFAULT_STYLE = ("AGT", 200, 200, 200)  # grey for unknown types


class GamaAgentVisualizer:
    """
    Consumes GAMA state events from Kafka and draws debug markers
    in CARLA at the geographic positions of GAMA agents.
    """

    def __init__(self, connector):
        """
        Args:
            connector: A BaseCarlaConnector (real or mock). Provides
                       .world and .carla_map for drawing and coordinate conversion.
        """
        self._connector = connector
        self._consumer: Optional[Consumer] = None
        self._running = False
        self._thread: Optional[threading.Thread] = None

        # Thread-safe buffer for the latest batch of visible agents
        self._lock = threading.Lock()
        self._latest_agents: List[Dict[str, Any]] = []
        self._latest_cycle: int = -1
        
        # State tracking for real spawned actors (GAMA ID -> carla.Actor)
        self._spawned_actors: Dict[str, Any] = {}
        
        # Flag to print the raw JSON of the very first agent for debugging
        self._first_agent_printed = False

        # Stats (updated per Kafka message)
        self._total_received = 0
        self._total_displayed = 0
        self._total_skipped_passengers = 0
        self._total_skipped_invalid = 0
        self._messages_processed = 0

    # ───────────────────────────────────────────────────────────────
    #  Lifecycle
    # ───────────────────────────────────────────────────────────────

    def start(self):
        """Initialize the Kafka consumer and start the background polling thread."""
        if not settings.GAMA_VIS_ENABLED:
            logger.info("GAMA visualization is disabled (GAMA_VIS_ENABLED=false)")
            return

        logger.info(
            "Starting GAMA Agent Visualizer — topic='%s', max_agents=%d, "
            "marker_lifetime=%.1fs, z_offset=%.1fm",
            settings.KAFKA_GAMA_STATE_TOPIC,
            settings.GAMA_VIS_MAX_AGENTS,
            settings.GAMA_VIS_MARKER_LIFETIME,
            settings.GAMA_VIS_Z_OFFSET,
        )

        self._consumer = Consumer({
            "bootstrap.servers": settings.KAFKA_BROKER,
            "group.id": "carla-adapter-gama-vis",
            "auto.offset.reset": "latest",
        })
        self._consumer.subscribe([settings.KAFKA_GAMA_STATE_TOPIC])
        self._running = True

        self._thread = threading.Thread(target=self._consume_loop, daemon=True)
        self._thread.start()
        logger.info("GAMA Agent Visualizer started.")

    def stop(self):
        """Stop the consumer loop and clean up."""
        self._running = False
        if self._consumer:
            try:
                self._consumer.close()
            except Exception:
                pass
                
        # Clean up all spawned actors before exiting
        import carla
        if getattr(self, '_connector', None) and getattr(self._connector, '_client', None):
            try:
                client = self._connector._client
                actors_to_destroy = list(self._spawned_actors.values())
                if actors_to_destroy:
                    logger.info("Cleaning up %d spawned GAMA pedestrians...", len(actors_to_destroy))
                    client.apply_batch([carla.command.DestroyActor(x) for x in actors_to_destroy])
            except Exception as e:
                logger.error("Failed to clean up GAMA pedestrians: %s", e)
        self._spawned_actors.clear()

        logger.info(
            "GAMA Visualizer stopped — total messages=%d, agents_received=%d, "
            "displayed=%d, skipped_passengers=%d, skipped_invalid=%d",
            self._messages_processed,
            self._total_received,
            self._total_displayed,
            self._total_skipped_passengers,
            self._total_skipped_invalid,
        )

    # ───────────────────────────────────────────────────────────────
    #  Kafka Consumer Loop (background thread)
    # ───────────────────────────────────────────────────────────────

    def _consume_loop(self):
        """Poll Kafka for gama-state messages. Runs in a background thread."""
        while self._running:
            try:
                msg = self._consumer.poll(timeout=1.0)
                if msg is None:
                    continue
                if msg.error():
                    if msg.error().code() != KafkaError._PARTITION_EOF:
                        logger.error("Kafka error (gama-state): %s", msg.error())
                    continue

                raw = msg.value().decode("utf-8")
                
                # We received at least one message!
                self._total_received += 1
                
                if self._total_received == 1:
                    logger.info("✅ FIRST GAMA MESSAGE RECEIVED FROM KAFKA!")
                self._process_message(raw)

            except Exception as e:
                logger.error("Error in GAMA visualizer consumer: %s", e, exc_info=True)

    def _process_message(self, raw: str):
        """Parse a GamaStateEvent JSON and extract visible agents."""
        try:
            event = json.loads(raw)
        except json.JSONDecodeError as e:
            logger.error("Failed to parse gama-state JSON: %s", e)
            return

        payload = event.get("payload")
        if not payload:
            return

        agents = payload.get("agents", [])
        if not agents:
            # This message type doesn't contain agents (e.g., traffic signals, roads)
            return

        cycle = payload.get("cycle", payload.get("tickNumber", -1))
        self._messages_processed += 1

        # ── Filter and limit ──
        visible = []
        skipped_passengers = 0
        skipped_invalid = 0

        for agent in agents:
            agent_id = agent.get("id", "?")
            agent_type = agent.get("type", "unknown")
            host_id = agent.get("hostId")
            
            # DEBUG: Print the raw agent payload once
            if not self._first_agent_printed:
                logger.info("🕵️ RAW AGENT PAYLOAD FROM KAFKA: %s", agent)
                self._first_agent_printed = True
            
            # Extract GPS coordinates. mobilitySimulator sends nested GPSCoordinates.
            gps_obj = agent.get("GPSCoordinates")
            if gps_obj and isinstance(gps_obj, dict):
                lon = gps_obj.get("lon", 0.0)
                lat = gps_obj.get("lat", 0.0)
            else:
                # Fallback for older Traffic and Pollution model
                lon = agent.get("x", 0.0)
                lat = agent.get("y", 0.0)

            # Skip passengers (pedestrians inside vehicles/bikes)
            if agent_type == "pedestrian" and host_id:
                skipped_passengers += 1
                continue
                
            # As requested: filter out cars, bikes, buses to keep it lightweight
            if agent_type != "pedestrian":
                continue

            # Validate coordinates
            if lat == 0.0 or lon == 0.0:
                logger.warning(
                    "⚠️ Agent %s (type=%s) has zero lat/lon (lat=%.6f, lon=%.6f) — skipping",
                    agent_id, agent_type, lat, lon
                )
                skipped_invalid += 1
                continue

            # Sanity check: lat should be ~43.x for Toulouse, lon should be ~1.x
            if not (-90 <= lat <= 90) or not (-180 <= lon <= 180):
                logger.warning(
                    "⚠️ Agent %s has out-of-range coords (lat=%.6f, lon=%.6f) — skipping",
                    agent_id, lat, lon
                )
                skipped_invalid += 1
                continue

            visible.append({
                "id": agent_id,
                "type": agent_type,
                "lat": lat,
                "lon": lon,
                "speed": agent.get("speed", 0.0),
                "heading": agent.get("heading", 0.0),
            })

            # Respect max agent limit
            if len(visible) >= settings.GAMA_VIS_MAX_AGENTS:
                break

        # ── Update stats ──
        self._total_received += len(agents)
        self._total_skipped_passengers += skipped_passengers
        self._total_skipped_invalid += skipped_invalid
        self._total_displayed += len(visible)

        # ── Store in thread-safe buffer ──
        with self._lock:
            self._latest_agents = visible
            self._latest_cycle = cycle

        logger.info(
            "📡 GAMA tick %d — received=%d | displaying=%d | "
            "skipped_passengers=%d | skipped_invalid=%d",
            cycle, len(agents), len(visible),
            skipped_passengers, skipped_invalid,
        )

    # ───────────────────────────────────────────────────────────────
    #  Drawing (called from main thread)
    # ───────────────────────────────────────────────────────────────

    def draw_agents(self):
        """
        Read the latest agent buffer and draw debug markers in CARLA.
        Must be called from the main thread (or synchronized with CARLA's world tick).
        """
        if not settings.GAMA_VIS_ENABLED:
            return

        # Get the latest agents from the buffer
        with self._lock:
            agents = list(self._latest_agents)
            cycle = self._latest_cycle

        if not agents:
            return

        world = self._connector.world
        carla_map = self._connector.carla_map

        # Mock mode: no CARLA world available, just log
        if world is None or carla_map is None:
            if agents:
                logger.info(
                    "🚗 MOCK DRAW — cycle=%d | would draw %d GAMA agents",
                    cycle, len(agents),
                )
                for a in agents[:5]:  # Log first 5 for debugging
                    style = AGENT_STYLES.get(a["type"], DEFAULT_STYLE)
                    logger.info(
                        "   MOCK → [%s] id=%s at (lat=%.6f, lon=%.6f) speed=%.1f",
                        style[0], a["id"], a["lat"], a["lon"], a["speed"],
                    )
                if len(agents) > 5:
                    logger.info("   ... and %d more agents", len(agents) - 5)
            return

        # ── Real CARLA mode: convert and draw ──
        import carla

        drawn = 0
        for agent in agents:
            try:
                # 🎯 DIGITAL TWIN ALIGNMENT OFFSET 🎯
                # We subtract the offset so that GAMA's absolute coordinates 
                # (e.g. 43.5645) map correctly onto CARLA's shifted map origin.
                MAP_LAT_OFFSET = settings.GAMA_VIS_LAT_OFFSET
                MAP_LON_OFFSET = settings.GAMA_VIS_LON_OFFSET
                
                lat = agent["lat"] - MAP_LAT_OFFSET
                lon = agent["lon"] - MAP_LON_OFFSET

                # ⚠️ FIX : CARLA's transform_from_geolocation is completely broken for this map due to missing OpenDRIVE PROJ strings!
                # It sends agents to the edge of the world. We bypass it by doing a linear Earth projection in meters.
                
                # Reference point (The Roundabout) in CARLA's Raw Absolute WGS84
                REF_LAT = 43.55287609
                REF_LON = 1.45950994
                # Reference point (The Roundabout) in CARLA Local 3D
                REF_X = 433.72
                REF_Y = -634.73
                
                # Conversion constants at Latitude 43.55 (Toulouse)
                METERS_PER_DEG_LAT = 111320.0
                METERS_PER_DEG_LON = 80681.0  # 111320 * cos(43.55 degrees)
                
                delta_lat = lat - REF_LAT
                delta_lon = lon - REF_LON
                
                delta_y_meters = delta_lat * METERS_PER_DEG_LAT
                delta_x_meters = delta_lon * METERS_PER_DEG_LON
                
                # In CARLA, Y axis points South (so increasing Latitude means decreasing Y)
                # X axis points East (so increasing Longitude means increasing X)
                calc_x = REF_X + delta_x_meters
                calc_y = REF_Y - delta_y_meters
                
                c_loc = carla.Location(x=calc_x, y=calc_y, z=0.0)

                # Snap to nearest waypoint for correct Z (avoid underground/floating)
                waypoint = carla_map.get_waypoint(
                    c_loc,
                    project_to_road=True,
                    lane_type=carla.LaneType.Any,
                )

                if waypoint:
                    draw_location = waypoint.transform.location
                else:
                    # Fallback: use raw location
                    draw_location = c_loc
                    # ⚠️ FIX: The roundabout is at Z=44.18. If we use Z=0.0, the markers are 44m underground!
                    draw_location.z = 45.0

                # Apply Z offset slightly to avoid spawning exactly underground
                draw_location.z += 0.5

                # Calculate rotation from GAMA heading
                # GAMA heading: 0 is East, 90 is South, 180 is West, 270 is North (Standard math angles)
                # CARLA yaw: 0 is East, 90 is South, -180/180 is West, -90 is North
                yaw = agent.get("heading", 0.0)
                
                target_transform = carla.Transform(draw_location, carla.Rotation(yaw=yaw))

                agent_id = agent['id']
                if agent_id in self._spawned_actors:
                    # Update existing pedestrian
                    actor = self._spawned_actors[agent_id]
                    if actor.is_alive:
                        actor.set_transform(target_transform)
                        # We can also set velocity if we want smooth walking between ticks
                        # But teleporting matches GAMA's discrete steps perfectly
                        drawn += 1
                    else:
                        # Dead actor? Remove it so it spawns again
                        del self._spawned_actors[agent_id]
                else:
                    # Spawn new pedestrian
                    import random
                    bp_library = world.get_blueprint_library()
                    ped_bps = bp_library.filter("walker.pedestrian.*")
                    if ped_bps:
                        bp = random.choice(ped_bps)
                        if bp.has_attribute('is_invincible'):
                            bp.set_attribute('is_invincible', 'true')
                        
                        actor = world.try_spawn_actor(bp, target_transform)
                        if actor:
                            # Disable physics for OpenDRIVE-only maps (no ground collision)
                            # Set GAMA_VIS_DISABLE_PHYSICS=false when using a full 3D map
                            if settings.GAMA_VIS_DISABLE_PHYSICS:
                                actor.set_simulate_physics(False)
                            self._spawned_actors[agent_id] = actor
                            drawn += 1
                        else:
                            logger.debug("Failed to spawn pedestrian %s at %s", agent_id, target_transform.location)

                # Removed the bright glowing bounding box as the user confirmed 3D models are visible when zooming in.

                # Draw the text above their head (No emojis, CARLA doesn't support them!)
                text_location = carla.Location(
                    x=draw_location.x,
                    y=draw_location.y,
                    z=draw_location.z + 2.5
                )
                
                speed_str = ""
                if agent.get("speed", 0) > 0:
                    speed_str = f" {agent['speed']:.1f}km/h"
                    
                display_text = f"[PED] {agent_id}{speed_str}"
                
                world.debug.draw_string(
                    text_location,
                    display_text,
                    draw_shadow=True,
                    color=carla.Color(255, 255, 255),
                    life_time=settings.GAMA_VIS_MARKER_LIFETIME,
                )
                
                # Debug logging for the first agent to verify positioning
                if drawn == 1 and len(self._spawned_actors) == 1:
                    logger.info("Real Pedestrian[0] spawned at CARLA Local: X=%.2f, Y=%.2f, Z=%.2f",
                                 draw_location.x, draw_location.y, draw_location.z)

            except Exception as e:
                logger.error(
                    "Failed to process agent %s (lat=%.6f, lon=%.6f): %s",
                    agent.get("id", "?"), agent.get("lat", 0), agent.get("lon", 0), e,
                )

        # ── Cleanup departed agents ──
        # Find agents in CARLA that are no longer in the GAMA payload
        current_gama_ids = {a["id"] for a in agents}
        despawn_ids = []
        actors_to_destroy = []
        
        for g_id, actor in self._spawned_actors.items():
            if g_id not in current_gama_ids:
                despawn_ids.append(g_id)
                if actor.is_alive:
                    actors_to_destroy.append(actor)
                    
        if actors_to_destroy:
            if getattr(self._connector, '_client', None):
                client = self._connector._client
                client.apply_batch([carla.command.DestroyActor(x) for x in actors_to_destroy])
            for g_id in despawn_ids:
                del self._spawned_actors[g_id]
                
        if drawn > 0 or actors_to_destroy:
            logger.debug(
                "🎨 GAMA Sync: %d updated/spawned, %d destroyed (cycle=%d)",
                drawn, len(actors_to_destroy), cycle,
            )

    # ───────────────────────────────────────────────────────────────
    #  Stats (for health/status API)
    # ───────────────────────────────────────────────────────────────

    @property
    def messages_processed(self) -> int:
        return self._messages_processed

    @property
    def total_agents_received(self) -> int:
        return self._total_received

    @property
    def total_agents_displayed(self) -> int:
        return self._total_displayed

    @property
    def current_agent_count(self) -> int:
        with self._lock:
            return len(self._latest_agents)
