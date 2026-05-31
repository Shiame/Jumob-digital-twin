"""
CARLA Adapter — Application Entrypoint
========================================
Main application that orchestrates:
1. FastAPI server (background thread) for health/status monitoring
2. CARLA connector (real or mock) for simulation data extraction
3. Data transformer for structuring raw data into events
4. Kafka producer for publishing events to the message bus
5. Kafka command consumer for receiving orchestrator commands

Usage:
    # Mock mode (no GPU needed):
    MOCK_MODE=true python main.py

    # Real CARLA mode (requires CARLA server running):
    python main.py
"""

import sys
import time
import signal
import logging
import threading
import uvicorn
from fastapi import FastAPI

from config import settings
from services.carla_connector import create_connector
from services.kafka_producer import KafkaProducerService
from services.kafka_consumer import KafkaCommandConsumer
from services.data_transformer import build_event
from services.gama_visualizer import GamaAgentVisualizer
from api.health import router as health_router, register_services

# ═══════════════════════════════════════════════════════════════
#  Logging Configuration
# ═══════════════════════════════════════════════════════════════

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)-7s] %(name)s — %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("carla-adapter")

# ═══════════════════════════════════════════════════════════════
#  FastAPI Application
# ═══════════════════════════════════════════════════════════════

app = FastAPI(
    title="CARLA Adapter — Digital Twin Microservice",
    description="Bridges the CARLA 3D simulator with Apache Kafka for the MIDOC Digital Twin.",
    version="2.0.0",
)
app.include_router(health_router)

# ═══════════════════════════════════════════════════════════════
#  Graceful Shutdown
# ═══════════════════════════════════════════════════════════════

_shutdown_event = threading.Event()


def _signal_handler(signum, frame):
    """Handle SIGINT/SIGTERM for graceful shutdown."""
    logger.info("Shutdown signal received (signal %d). Stopping...", signum)
    _shutdown_event.set()


signal.signal(signal.SIGINT, _signal_handler)
signal.signal(signal.SIGTERM, _signal_handler)

# ═══════════════════════════════════════════════════════════════
#  API Server (background thread)
# ═══════════════════════════════════════════════════════════════


def start_api_server():
    """Run the FastAPI server in a background daemon thread."""
    logger.info("Starting FastAPI server on %s:%d ...", settings.API_HOST, settings.API_PORT)
    uvicorn.run(
        app,
        host=settings.API_HOST,
        port=settings.API_PORT,
        log_level="warning",
    )


# ═══════════════════════════════════════════════════════════════
#  Main Simulation Loop
# ═══════════════════════════════════════════════════════════════


def main():
    """Main entrypoint: connect, extract, transform, publish, repeat."""

    # --- Banner ---
    logger.info("=" * 60)
    logger.info("  CARLA ADAPTER v2.0 — MIDOC Digital Twin")
    logger.info("  Mode: %s", "🚗 MOCK (no GPU)" if settings.MOCK_MODE else "🎮 REAL CARLA")
    logger.info("  Kafka: %s → topic '%s'", settings.KAFKA_BROKER, settings.KAFKA_TOPIC)
    logger.info("  Commands: listening on '%s'", settings.KAFKA_COMMANDS_TOPIC)
    logger.info("  GAMA Vis: %s (topic='%s', max=%d agents)",
                "ENABLED" if settings.GAMA_VIS_ENABLED else "DISABLED",
                settings.KAFKA_GAMA_STATE_TOPIC, settings.GAMA_VIS_MAX_AGENTS)
    logger.info("  Tick interval: %.2fs (%.0f Hz)", settings.TICK_INTERVAL, 1 / settings.TICK_INTERVAL)
    logger.info("  API: http://%s:%d/api/status", settings.API_HOST, settings.API_PORT)
    logger.info("=" * 60)

    # --- Initialize services ---
    connector = create_connector()
    kafka = KafkaProducerService()
    command_consumer = KafkaCommandConsumer(connector)
    gama_visualizer = GamaAgentVisualizer(connector)

    # Register services for the API health endpoints
    register_services(connector, kafka, settings.MOCK_MODE)

    # --- Connect to CARLA ---
    max_retries = 5
    for attempt in range(1, max_retries + 1):
        if connector.connect():
            break
        logger.warning("Retry %d/%d — waiting 5s before reconnecting...", attempt, max_retries)
        time.sleep(5)
    else:
        logger.error("Could not connect to CARLA after %d attempts. Exiting.", max_retries)
        sys.exit(1)

    # --- Connect to Kafka ---
    kafka.connect()

    # --- Start command consumer (background thread) ---
    command_consumer.start()

    # --- Start GAMA Agent Visualizer (background Kafka consumer) ---
    gama_visualizer.start()

    # --- Start API server in background ---
    api_thread = threading.Thread(target=start_api_server, daemon=True)
    api_thread.start()

    # --- Main loop ---
    logger.info("Starting simulation data streaming loop...")
    tick_count = 0

    try:
        while not _shutdown_event.is_set():
            # 1. Get current tick
            tick_number = connector.get_tick_number()

            # 2. Extract vehicle states
            vehicles = connector.get_vehicles()

            # 3. Extract collision events
            collisions = connector.get_collision_events()
            if collisions:
                logger.warning(" COLLISION DÉTECTÉE PAR LE CAPTEUR ! (Nombre: %d)", len(collisions))

            # 4. Build structured event (now includes collisions)
            event = build_event(
                tick_number=tick_number,
                vehicles=vehicles,
                map_name=connector.get_map_name(),
                collision_events=collisions,
            )

            # 5. Serialize and publish to Kafka
            event_json = event.model_dump_json()
            kafka.publish(event_json)

            # 5b. Draw GAMA agents in CARLA scene (reads from thread-safe buffer)
            gama_visualizer.draw_agents()

            tick_count += 1
            if tick_count % 50 == 0:
                logger.info(
                    "📡 Streaming... tick=%d | vehicles=%d | cmds_received=%d | kafka_total=%d",
                    tick_number,
                    len(vehicles),
                    command_consumer.commands_received,
                    kafka.messages_published,
                )

            # 6. Wait for next tick
            # Sync at 10Hz to ensure smooth real-time visualization of GAMA agents
            _shutdown_event.wait(timeout=0.1)

    except Exception as e:
        logger.error("Unexpected error in main loop: %s", e, exc_info=True)
    finally:
        logger.info("Shutting down CARLA Adapter...")
        gama_visualizer.stop()
        command_consumer.stop()
        kafka.flush()
        logger.info("CARLA Adapter stopped. Total ticks processed: %d", tick_count)


if __name__ == "__main__":
    main()
