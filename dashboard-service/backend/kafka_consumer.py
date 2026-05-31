import json
import logging
import threading
from confluent_kafka import Consumer, KafkaError
import asyncio

logger = logging.getLogger(__name__)

class DashboardKafkaConsumer:
    def __init__(self, broker: str, topics: list[str], event_queue: asyncio.Queue, loop: asyncio.AbstractEventLoop):
        self._broker = broker
        self._topics = topics
        self._queue = event_queue
        self._loop = loop
        
        self._consumer = Consumer({
            'bootstrap.servers': self._broker,
            'group.id': 'dashboard-service-group',
            'auto.offset.reset': 'latest', # We only care about live data
            'enable.auto.commit': True
        })
        self._running = False
        self._thread = None

    def start(self):
        if self._running:
            return
            
        try:
            self._consumer.subscribe(self._topics)
            self._running = True
            self._thread = threading.Thread(target=self._consume_loop, daemon=True)
            self._thread.start()
            logger.info(f"Dashboard Kafka Consumer started for topics: {self._topics}")
        except Exception as e:
            logger.error(f"Failed to start Kafka consumer: {e}")

    def stop(self):
        self._running = False
        if self._thread:
            self._thread.join(timeout=2.0)
        self._consumer.close()
        logger.info("Dashboard Kafka Consumer stopped")

    def _consume_loop(self):
        while self._running:
            try:
                msg = self._consumer.poll(timeout=0.1)
                if msg is None:
                    continue
                if msg.error():
                    if msg.error().code() != KafkaError._PARTITION_EOF:
                        logger.error(f"Kafka error: {msg.error()}")
                    continue
                
                topic = msg.topic()
                raw_value = msg.value().decode('utf-8')
                
                try:
                    payload = json.loads(raw_value)
                    
                    # Create a generic event structure
                    event = {
                        "topic": topic,
                        "timestamp": msg.timestamp()[1] if msg.timestamp()[0] != 0 else 0,
                        "payload": payload
                    }
                    
                    # Thread-safe way to put item into asyncio Queue
                    asyncio.run_coroutine_threadsafe(self._queue.put(event), self._loop)
                    
                except json.JSONDecodeError:
                    logger.warning(f"Failed to decode JSON from topic {topic}: {raw_value[:50]}")
                    
            except Exception as e:
                logger.error(f"Error in consume loop: {e}", exc_info=True)
