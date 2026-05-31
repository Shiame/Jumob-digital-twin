"""
CARLA Adapter — Health & Status REST API
==========================================
FastAPI router providing monitoring endpoints for the CARLA Adapter.
Mirrors the /api/health and /api/status endpoints of the GAMA Adapter (Spring Boot).
"""

import logging
from fastapi import APIRouter

logger = logging.getLogger("carla-adapter.api")

router = APIRouter(prefix="/api", tags=["Monitoring"])

# These will be set by main.py after service initialization
_carla_connector = None
_kafka_producer = None
_mock_mode = False


def register_services(carla_connector, kafka_producer, mock_mode: bool):
    """Register service instances for status reporting."""
    global _carla_connector, _kafka_producer, _mock_mode
    _carla_connector = carla_connector
    _kafka_producer = kafka_producer
    _mock_mode = mock_mode


@router.get("/health")
def health():
    """
    Health check endpoint.
    Returns UP if CARLA connector is active, DOWN otherwise.
    """
    carla_ok = _carla_connector.is_connected() if _carla_connector else False
    kafka_ok = _kafka_producer.is_connected if _kafka_producer else False

    is_healthy = carla_ok and kafka_ok

    return {
        "status": "UP" if is_healthy else "DOWN",
        "carlaConnection": "CONNECTED" if carla_ok else "DISCONNECTED",
        "kafkaConnection": "CONNECTED" if kafka_ok else "DISCONNECTED",
    }


@router.get("/status")
def status():
    """
    Detailed status endpoint.
    Returns full adapter state including connection info, message counts, and mode.
    """
    return {
        "service": "carla-adapter",
        "mockMode": _mock_mode,
        "carlaConnection": _carla_connector.is_connected() if _carla_connector else False,
        "carlaMap": _carla_connector.get_map_name() if _carla_connector else "N/A",
        "kafkaConnected": _kafka_producer.is_connected if _kafka_producer else False,
        "messagesPublished": _kafka_producer.messages_published if _kafka_producer else 0,
        "lastError": _kafka_producer.last_error if _kafka_producer else None,
    }
