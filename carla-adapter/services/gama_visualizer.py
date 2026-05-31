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
            lon = agent.get("x", 0.0)  # x = longitude
            lat = agent.get("y", 0.0)  # y = latitude

            # Skip passengers (pedestrians inside vehicles/bikes)
            if agent_type == "pedestrian" and host_id:
                skipped_passengers += 1
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
                lat = agent["lat"] + settings.GAMA_VIS_LAT_OFFSET
                lon = agent["lon"] + settings.GAMA_VIS_LON_OFFSET

                # Convert geographic coordinates to CARLA transform
                geo = carla.GeoLocation(
                    latitude=lat,
                    longitude=lon,
                    altitude=0.0,
                )
                transform = carla_map.geolocation_to_transform(geo)

                # Snap to nearest waypoint for correct Z (avoid underground/floating)
                waypoint = carla_map.get_waypoint(
                    transform.location,
                    project_to_road=True,
                    lane_type=carla.LaneType.Any,
                )

                if waypoint:
                    draw_location = waypoint.transform.location
                else:
                    # Fallback: use raw transform location
                    draw_location = transform.location

                # Apply Z offset so markers float above the road surface
                draw_location.z += settings.GAMA_VIS_Z_OFFSET

                # Get visual style
                style = AGENT_STYLES.get(agent["type"], DEFAULT_STYLE)
                label, r, g, b = style

                # Build display text
                display_text = f"[{label}] {agent['id']}"
                if agent["speed"] > 0:
                    display_text += f" {agent['speed']:.0f}km/h"

                color = carla.Color(r=r, g=g, b=b, a=255)

                # Draw the label string
                world.debug.draw_string(
                    draw_location,
                    display_text,
                    draw_shadow=True,
                    color=color,
                    life_time=settings.GAMA_VIS_MARKER_LIFETIME,
                )

                # Draw a point marker slightly below the text for visibility
                point_location = carla.Location(
                    x=draw_location.x,
                    y=draw_location.y,
                    z=draw_location.z - 0.3,
                )
                world.debug.draw_point(
                    point_location,
                    size=0.15,
                    color=color,
                    life_time=settings.GAMA_VIS_MARKER_LIFETIME,
                )

                drawn += 1

            except Exception as e:
                logger.error(
                    "Failed to draw agent %s (lat=%.6f, lon=%.6f): %s",
                    agent.get("id", "?"), agent.get("lat", 0), agent.get("lon", 0), e,
                )

        if drawn > 0:
            logger.debug(
                "🎨 Drew %d/%d GAMA agents in CARLA (cycle=%d)",
                drawn, len(agents), cycle,
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
