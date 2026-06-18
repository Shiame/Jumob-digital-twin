import asyncio
import logging
import os
from contextlib import asynccontextmanager
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from kafka_consumer import DashboardKafkaConsumer
import json

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)-5s] %(name)s - %(message)s'
)
logger = logging.getLogger("dashboard-backend")

# Shared queue for events from Kafka to WebSockets
event_queue = asyncio.Queue()
kafka_consumer = None
active_connections: list[WebSocket] = []

KAFKA_BROKER = os.getenv("KAFKA_BROKER", "localhost:9092")
TOPICS = ["gama-state", "carla-state", "carla-commands"]

@asynccontextmanager
async def lifespan(app: FastAPI):
    global kafka_consumer
    loop = asyncio.get_running_loop()
    
    # Initialize and start Kafka consumer
    kafka_consumer = DashboardKafkaConsumer(
        broker=KAFKA_BROKER,
        topics=TOPICS,
        event_queue=event_queue,
        loop=loop
    )
    kafka_consumer.start()
    
    # Start the broadcaster task
    broadcaster_task = asyncio.create_task(broadcast_events())
    
    yield
    
    # Shutdown
    kafka_consumer.stop()
    broadcaster_task.cancel()

app = FastAPI(lifespan=lifespan)

# Add CORS to allow the React frontend to connect
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

async def broadcast_events():
    """Reads events from the queue and sends them to all connected websockets."""
    logger.info("Broadcaster task started")
    try:
        while True:
            event = await event_queue.get()
            
            if not active_connections:
                continue
                
            # Serialize once
            event_json = json.dumps(event)
            
            # Send to all clients
            dead_connections = []
            for connection in active_connections:
                try:
                    await connection.send_text(event_json)
                except Exception:
                    dead_connections.append(connection)
            
            # Cleanup dead connections
            for dead in dead_connections:
                if dead in active_connections:
                    active_connections.remove(dead)
    except asyncio.CancelledError:
        logger.info("Broadcaster task cancelled")

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    active_connections.append(websocket)
    logger.info(f"New client connected. Total clients: {len(active_connections)}")
    
    try:
        while True:
            # We don't really expect messages from the client, but we need to keep the connection open
            # and detect disconnects
            await websocket.receive_text()
    except WebSocketDisconnect:
        active_connections.remove(websocket)
        logger.info(f"Client disconnected. Total clients: {len(active_connections)}")

from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse

@app.get("/api/status")
def get_status():
    return {
        "status": "running",
        "kafka_broker": KAFKA_BROKER,
        "topics": TOPICS,
        "connected_clients": len(active_connections)
    }

# Serve the React frontend static files
frontend_dist = os.path.join(os.path.dirname(__file__), "..", "frontend", "dist")

if os.path.exists(frontend_dist):
    app.mount("/assets", StaticFiles(directory=os.path.join(frontend_dist, "assets")), name="assets")

    from fastapi import HTTPException

    @app.get("/{catchall:path}")
    def serve_react_app(catchall: str):
        if catchall.startswith("api/"):
            raise HTTPException(status_code=404, detail="Not Found")
        return FileResponse(os.path.join(frontend_dist, "index.html"))
else:
    logger.warning(f"Frontend dist folder not found at {frontend_dist}. Please run 'npm run build' in the frontend directory.")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8085, reload=False)
