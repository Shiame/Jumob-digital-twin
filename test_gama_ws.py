import asyncio
import websockets
import json

async def test():
    uri = "ws://localhost:6868"
    try:
        async with websockets.connect(uri) as websocket:
            print("Connected to GAMA!")
            # Wait for any init message (like ConnectionSuccessful)
            # Try to evaluate the expression directly
            expr = "ask osmRoad where (each.uniqueID_ = '2641') { maxspeed <- 0.0; }"
            # Note: We need a valid exp_id for GAMA Server.
            # But just to test, let's see if we can get the exp_id first.
            cmd = json.dumps({
                "type": "expression",
                "exp_id": "WILL_FAIL_IF_NOT_CORRECT",
                "expr": expr
            })
            print(f"Sending: {cmd}")
            await websocket.send(cmd)
            
            # Receive response
            for _ in range(3):
                try:
                    response = await asyncio.wait_for(websocket.recv(), timeout=2.0)
                    print(f"Received: {response[:200]}")
                except asyncio.TimeoutError:
                    break
    except Exception as e:
        print(f"Connection failed: {e}")

asyncio.run(test())
