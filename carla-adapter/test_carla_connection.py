#!/usr/bin/env python3
"""
Simple script to test connectivity between CARLA adapter and the CARLA server.
Reads configuration from .env and tries to establish a client connection.
"""
import os
import sys

# Add current directory to path
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

try:
    from config import settings
    print(f"✅ Loaded config successfully.")
    print(f"   CARLA Host: {settings.CARLA_HOST}")
    print(f"   CARLA Port: {settings.CARLA_PORT}")
    print(f"   CARLA Timeout: {settings.CARLA_TIMEOUT}s")
    print(f"   Mock Mode: {settings.MOCK_MODE}")
except Exception as e:
    print(f"❌ Failed to load configuration: {e}")
    sys.exit(1)

print("\n--- Phase 1: Checking CARLA Python Library ---")
try:
    import carla
    print(f"✅ 'carla' library is successfully installed and importable.")
except ImportError:
    print("❌ 'carla' python package is NOT installed or not found in the current Python environment.")
    print("   Please check your virtual environment or install it using requirements.txt.")
    sys.exit(1)

print("\n--- Phase 2: Testing Connection to CARLA Server ---")
if settings.MOCK_MODE:
    print("⚠️  MOCK_MODE is set to 'true' in your configuration/environment.")
    print("   To test real connectivity, run this script with MOCK_MODE=false.")
    print("   Example: MOCK_MODE=false python3 test_carla_connection.py")
    print("\n   [Simulating Mock Connection]")
    print("   ✅ Mock connection successful.")
    sys.exit(0)

try:
    print(f"Connecting to CARLA server at {settings.CARLA_HOST}:{settings.CARLA_PORT}...")
    client = carla.Client(settings.CARLA_HOST, settings.CARLA_PORT)
    client.set_timeout(settings.CARLA_TIMEOUT)
    
    # Try an active API call to verify the server is responsive
    world = client.get_world()
    carla_map = world.get_map()
    map_name = carla_map.name.split("/")[-1]
    
    print(f"✅ Successfully connected to CARLA!")
    print(f"   Active Map: {map_name}")
    print(f"   World Frame: {world.get_snapshot().frame}")
    
    # Check for vehicles
    vehicles = world.get_actors().filter("vehicle.*")
    print(f"   Current Vehicles: {len(vehicles)}")
    for v in vehicles:
        role = v.attributes.get("role_name", "")
        print(f"     - Vehicle ID {v.id}: {v.type_id} (role_name: '{role}')")
        
except Exception as e:
    print(f"❌ Failed to connect to CARLA server: {e}")
    print("\n💡 Troubleshooting Tips:")
    print("1. Is the CARLA simulator running? (e.g. `./CarlaUE4.sh -RenderOffScreen`)")
    print("2. Is the port correct? CARLA uses two ports: the base port (usually 2000) and base+1 (usually 2001). Both must be open/forwarded.")
    print("3. If CARLA is running on OcciData remote cluster, make sure your SSH tunnel is active:")
    print(f"   ssh -L {settings.CARLA_PORT}:localhost:{settings.CARLA_PORT} -L {settings.CARLA_PORT+1}:localhost:{settings.CARLA_PORT+1} cbouazza@occidata-cluster.irit.fr")
    sys.exit(1)
