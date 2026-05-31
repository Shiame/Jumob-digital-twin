import carla
import json
import time
from confluent_kafka import Producer

# Configuration
CARLA_HOST = '127.0.0.1'
CARLA_PORT = 2000
KAFKA_BROKER = 'localhost:9092'
KAFKA_TOPIC = 'carla-state'
TICK_INTERVAL = 0.1  # 10 Hz

def delivery_report(err, msg):
    if err is not None:
        print(f"Failed to deliver message: {err}")
    else:
        pass # print(f"Message produced to {msg.topic()}")

def get_actor_state(actor):
    transform = actor.get_transform()
    velocity = actor.get_velocity()
    return {
        "id": actor.id,
        "type": actor.type_id,
        "x": transform.location.x,
        "y": transform.location.y,
        "z": transform.location.z,
        "pitch": transform.rotation.pitch,
        "yaw": transform.rotation.yaw,
        "roll": transform.rotation.roll,
        "vx": velocity.x,
        "vy": velocity.y,
        "vz": velocity.z
    }

def main():
    print(f"Connecting to CARLA Server at {CARLA_HOST}:{CARLA_PORT}...")
    try:
        client = carla.Client(CARLA_HOST, CARLA_PORT)
        client.set_timeout(10.0)
        world = client.get_world()
        print("Connected to CARLA successfully!")
    except RuntimeError as e:
        print(f"Could not connect to CARLA: {e}")
        return

    print(f"Connecting to Kafka Broker at {KAFKA_BROKER}...")
    producer = Producer({'bootstrap.servers': KAFKA_BROKER})

    print(f"Starting to stream data to topic '{KAFKA_TOPIC}'...")

    try:
        while True:
            actors = world.get_actors().filter('vehicle.*')
            
            payload = {
                "timestamp": time.time(),
                "world_snapshot_frame": world.get_snapshot().frame,
                "vehicles": [get_actor_state(v) for v in actors]
            }
            
            # Convert to JSON and publish
            message_str = json.dumps(payload)
            producer.produce(KAFKA_TOPIC, message_str.encode('utf-8'), callback=delivery_report)
            producer.poll(0)
            
            time.sleep(TICK_INTERVAL)
            
    except KeyboardInterrupt:
        print("Simulation streaming stopped by user.")
    finally:
        producer.flush()

if __name__ == '__main__':
    main()
