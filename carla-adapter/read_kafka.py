from confluent_kafka import Consumer, KafkaError
import json
import sys

def main():
    conf = {
        'bootstrap.servers': 'localhost:9092',
        'group.id': 'debug-group-12345',
        'auto.offset.reset': 'latest'
    }
    
    consumer = Consumer(conf)
    consumer.subscribe(['gama-state'])
    
    print("Waiting for 1 message from gama-state...")
    while True:
        msg = consumer.poll(1.0)
        if msg is None:
            continue
        if msg.error():
            print(f"Error: {msg.error()}")
            continue
        
        try:
            payload = json.loads(msg.value().decode('utf-8'))
            print("=== RAW GAMA MESSAGE ===")
            print(json.dumps(payload, indent=2))
            break
        except Exception as e:
            print(f"Failed to parse: {e}")
            break
            
    consumer.close()

if __name__ == '__main__':
    main()
