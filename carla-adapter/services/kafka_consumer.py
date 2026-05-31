"""
CARLA Adapter — Kafka Command Consumer
=========================================
Consumes commands from the orchestrator via the 'carla-commands' Kafka topic.
Dispatches each command to the appropriate handler on the CARLA connector.

For SET_SPEED_LIMIT commands, validates that the ego vehicle is on one of the
target CARLA road segments before applying the speed reduction.
"""

import json
import logging
import threading
from confluent_kafka import Consumer, KafkaError

from config import settings

logger = logging.getLogger("carla-adapter.command-consumer")


class KafkaCommandConsumer:
    """
    Kafka consumer that listens on 'carla-commands' topic and executes
    commands on the CARLA connector (real or mock).
    """

    def __init__(self, connector):
        self._connector = connector
        self._consumer = None
        self._running = False
        self._thread = None
        self._commands_received = 0

    def start(self):
        """Initialize the Kafka consumer and start listening in a background thread."""
        logger.info("Starting command consumer on topic '%s'...", settings.KAFKA_COMMANDS_TOPIC)

        self._consumer = Consumer({
            "bootstrap.servers": settings.KAFKA_BROKER,
            "group.id": "carla-adapter-commands",
            "auto.offset.reset": "latest",
        })
        self._consumer.subscribe([settings.KAFKA_COMMANDS_TOPIC])
        self._running = True

        self._thread = threading.Thread(target=self._consume_loop, daemon=True)
        self._thread.start()
        logger.info("Command consumer started.")

    def stop(self):
        """Stop the consumer loop."""
        self._running = False
        if self._consumer:
            self._consumer.close()

    def _consume_loop(self):
        """Main consumer loop — runs in a background thread."""
        while self._running:
            try:
                msg = self._consumer.poll(timeout=1.0)
                if msg is None:
                    continue
                if msg.error():
                    if msg.error().code() != KafkaError._PARTITION_EOF:
                        logger.error("Kafka error: %s", msg.error())
                    continue

                raw = msg.value().decode("utf-8")
                command = json.loads(raw)
                self._commands_received += 1

                cmd_type = command.get("commandType", "UNKNOWN")
                cmd_id = command.get("commandId", "?")

                # Only log every 10th command to avoid spam
                if self._commands_received == 1 or self._commands_received % 10 == 0:
                    logger.info("Command #%d: %s (%s) from '%s'",
                                self._commands_received, cmd_type, cmd_id, command.get("triggeredBy", "?"))

                self._dispatch(command)

            except Exception as e:
                logger.error("Error processing command: %s", e, exc_info=True)

    def _dispatch(self, command: dict):
        """Route the command to the appropriate handler on the connector."""
        cmd_type = command.get("commandType", "")

        if cmd_type == "SET_SPEED_LIMIT":
            self._handle_set_speed_limit(command)

        elif cmd_type == "CHANGE_TRAFFIC_LIGHT":
            tl_id = command.get("trafficLightId", "?")
            state = command.get("newState", "GREEN")
            self._connector.change_traffic_light(tl_id, state)
            logger.info("🚦 CHANGE_TRAFFIC_LIGHT → %s = %s", tl_id, state)

        elif cmd_type == "BLOCK_ZONE_ENTRY":
            zone = command.get("zoneId", "?")
            self._connector.block_zone_entry(zone)
            logger.info("🚧 BLOCK_ZONE_ENTRY → zone '%s' blocked", zone)

        elif cmd_type == "SPAWN_NPC":
            x = command.get("x", 0)
            y = command.get("y", 0)
            agent_type = command.get("agentType", "pieton")
            self._connector.spawn_npc(x, y, agent_type)
            logger.info("👤 SPAWN_NPC → %s at (%.1f, %.1f)", agent_type, x, y)

        else:
            logger.warning("Unknown command type: %s", cmd_type)

    def _handle_set_speed_limit(self, command: dict):
        """
        Handle SET_SPEED_LIMIT command.

        Two modes:
          1. Geo-proximity mode (no targetCarlaRoadIds):
             → Apply speed limit directly to ego vehicle
          2. Road-based mode (with targetCarlaRoadIds):
             → Check if ego is on target road, then apply
        """
        speed = command.get("maxSpeedKmh", 50)
        reason = command.get("reason", "UNKNOWN")
        triggered_by = command.get("triggeredBy", "?")
        target_roads = command.get("targetCarlaRoadIds", [])
        alignment_method = command.get("alignmentMethod", "")

        if not target_roads or alignment_method == "GEO_PROXIMITY":
            # ── Geo-proximity mode: apply directly to ego ──
            # Log only first occurrence to avoid spam (commands arrive every 2s)
            if self._commands_received <= 1:
                logger.info(
                    "GEO-PROXIMITY SET_SPEED_LIMIT: maxSpeed=%d km/h, reason=%s, peds=%s",
                    speed, reason, command.get("pedestrianCount", "?"),
                )
            self._connector.set_speed_limit_ego(speed_kmh=speed, reason=reason)

        else:
            # ── Road-based mode: check road first ──
            source_gama_road = command.get("sourceGamaRoadId", "?")
            logger.info(
                "🚗 ROAD-BASED SET_SPEED_LIMIT: sourceGamaRoad=%s, "
                "targetCarlaRoads=%s, maxSpeed=%d km/h, reason=%s",
                source_gama_road, target_roads, speed, reason,
            )
            self._connector.set_speed_limit_on_roads(
                speed_kmh=speed,
                target_road_ids=target_roads,
                source_gama_road=source_gama_road,
                reason=reason,
            )

    @property
    def commands_received(self):
        return self._commands_received
