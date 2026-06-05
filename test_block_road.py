from kafka import KafkaProducer
import json
import time

producer = KafkaProducer(
    bootstrap_servers=['localhost:9092'],
    value_serializer=lambda v: json.dumps(v).encode('utf-8')
)

command = {
    "commandType": "BLOCK_ROAD",
    "roadId": "2641",
    "blocked": True,
    "triggeredBy": "S2_COLLISION"
}

print("Publishing BLOCK_ROAD command to gama-commands topic...")
producer.send('gama-commands', command)
producer.flush()
print("Sent successfully! Check GAMA adapter logs and GAMA visualization.")
