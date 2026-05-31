"""
CARLA Adapter — Kafka Producer Service
========================================
Publishes serialized CarlaStateEvent JSON messages to Apache Kafka.
Wraps the confluent-kafka Producer with delivery reporting and graceful shutdown.
"""

import logging
from typing import Optional, Union
from confluent_kafka import Producer, KafkaError

from config import settings

logger = logging.getLogger("carla-adapter.kafka")


class KafkaProducerService:
    """Kafka producer that publishes CARLA state events."""

    def __init__(self):
        self._producer: Optional[Producer] = None
        self._messages_published: int = 0
        self._last_error: Optional[str] = None
        self._connected: bool = False

    def connect(self) -> None:
        """Initialize the Kafka producer."""
        logger.info("Connecting to Kafka broker at %s ...", settings.KAFKA_BROKER)
        try:
            self._producer = Producer({
                "bootstrap.servers": settings.KAFKA_BROKER,
                "client.id": "carla-adapter",
                "acks": "all",
                "retries": 3,
                "retry.backoff.ms": 500,
            })
            self._connected = True
            logger.info("Kafka producer initialized successfully.")
        except Exception as e:
            self._connected = False
            self._last_error = str(e)
            logger.error("Failed to initialize Kafka producer: %s", e)
            raise

    def _delivery_report(self, err, msg) -> None:
        """Callback for Kafka message delivery reports."""
        if err is not None:
            self._last_error = str(err)
            logger.error("Failed to deliver message to %s: %s", msg.topic(), err)
        else:
            self._messages_published += 1
            if self._messages_published % 100 == 0:
                logger.info(
                    "Published %d messages to topic '%s'",
                    self._messages_published,
                    msg.topic(),
                )

    def publish(self, message_json: str) -> None:
        """
        Publish a JSON message to the configured Kafka topic.
        
        Args:
            message_json: Serialized JSON string of a CarlaStateEvent
        """
        if not self._producer:
            logger.warning("Kafka producer not initialized, skipping publish.")
            return

        try:
            self._producer.produce(
                topic=settings.KAFKA_TOPIC,
                value=message_json.encode("utf-8"),
                callback=self._delivery_report,
            )
            self._producer.poll(0)
        except Exception as e:
            self._last_error = str(e)
            logger.error("Error publishing to Kafka: %s", e)

    def flush(self) -> None:
        """Flush all pending messages (call on shutdown)."""
        if self._producer:
            logger.info("Flushing Kafka producer (%d messages published so far)...", self._messages_published)
            self._producer.flush(timeout=5.0)
            logger.info("Kafka producer flushed.")

    # --- Status accessors ---

    @property
    def is_connected(self) -> bool:
        return self._connected

    @property
    def messages_published(self) -> int:
        return self._messages_published

    @property
    def last_error(self) -> Optional[str]:
        return self._last_error
