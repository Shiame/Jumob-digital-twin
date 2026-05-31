"""
Spawn Hero Vehicle — Target Road 1476
========================================
Spawns a hero (ego) vehicle on CARLA road_id=1476.
This road corresponds to GAMA roadId "2641" in our mapping.

The script:
  1. Connects to CARLA
  2. Gets the map and all waypoints
  3. Finds a waypoint on road_id=1476
  4. Spawns a Tesla Model 3 with role_name='hero' at that waypoint
  5. Enables autopilot
"""

import carla
import random
import time
import sys

# Target road_id(s) — these correspond to GAMA road 2641
TARGET_ROAD_IDS = [1476, 1205, 1475]


def main():
    client = carla.Client('localhost', 2000)
    client.set_timeout(10.0)
    world = client.get_world()
    carla_map = world.get_map()

    # 1. Choisir un modèle (Tesla Model 3)
    blueprint_library = world.get_blueprint_library()
    bp = blueprint_library.find('vehicle.tesla.model3')
    
    # IMPORTANT: On lui donne le rôle 'hero' pour que l'adaptateur le reconnaisse
    bp.set_attribute('role_name', 'hero')
    bp.set_attribute('color', '255,0,0')  # Rouge pour bien le voir

    # 2. Trouver un waypoint sur la route ciblée (road_id=1476)
    spawn_point = None
    all_waypoints = carla_map.generate_waypoints(5.0)  # every 5 meters

    # Try each target road in priority order
    for target_road_id in TARGET_ROAD_IDS:
        road_waypoints = [wp for wp in all_waypoints if wp.road_id == target_road_id]
        if road_waypoints:
            chosen_wp = random.choice(road_waypoints)
            spawn_point = chosen_wp.transform
            # Raise the vehicle slightly above ground to avoid collision on spawn
            spawn_point.location.z += 0.5
            print(f"✅ Found waypoint on road_id={target_road_id} at "
                  f"({spawn_point.location.x:.1f}, {spawn_point.location.y:.1f}, {spawn_point.location.z:.1f})")
            print(f"   Total waypoints on this road: {len(road_waypoints)}")
            break

    if spawn_point is None:
        # Fallback: list available road_ids for debugging
        road_ids = set(wp.road_id for wp in all_waypoints)
        print(f"❌ Could not find waypoints on target roads {TARGET_ROAD_IDS}")
        print(f"   Available road_ids in map ({len(road_ids)} total): {sorted(road_ids)[:50]}...")
        print("   Using random spawn point as fallback.")
        spawn_points = carla_map.get_spawn_points()
        spawn_point = random.choice(spawn_points) if spawn_points else carla.Transform()

    # 3. Spawner le véhicule
    vehicle = world.try_spawn_actor(bp, spawn_point)
    if vehicle is None:
        print("⚠️ Spawn failed at chosen point. Trying nearby location...")
        # Try a slight offset
        spawn_point.location.x += 2.0
        spawn_point.location.z += 1.0
        vehicle = world.try_spawn_actor(bp, spawn_point)

    if vehicle is None:
        print("❌ Failed to spawn vehicle. Falling back to default spawn points.")
        spawn_points = carla_map.get_spawn_points()
        for sp in spawn_points:
            vehicle = world.try_spawn_actor(bp, sp)
            if vehicle is not None:
                break

    if vehicle is None:
        print("❌ Could not spawn vehicle at all. Exiting.")
        sys.exit(1)

    # 4. Activer l'autopilot (pour qu'il roule tout seul)
    vehicle.set_autopilot(True)
    
    # 5. Vérifier la route actuelle
    wp = carla_map.get_waypoint(vehicle.get_location(), project_to_road=True)
    print(f"\n{'='*60}")
    print(f"  🚗 HERO VEHICLE SPAWNED SUCCESSFULLY!")
    print(f"  Vehicle ID:    {vehicle.id}")
    print(f"  Type:          {vehicle.type_id}")
    print(f"  Road ID:       {wp.road_id if wp else 'unknown'}")
    print(f"  Lane ID:       {wp.lane_id if wp else 'unknown'}")
    print(f"  Location:      ({vehicle.get_location().x:.1f}, {vehicle.get_location().y:.1f})")
    print(f"  Autopilot:     ON")
    print(f"{'='*60}")
    print(f"\n🚀 L'orchestrateur peut maintenant contrôler sa vitesse.")
    print(f"   Attendu: quand GAMA détecte des piétons sur roadId 2641,")
    print(f"   le véhicule sur CARLA road_id {wp.road_id if wp else '?'} ralentira.\n")

    try:
        while True:
            # Periodically log vehicle position and road
            wp = carla_map.get_waypoint(vehicle.get_location(), project_to_road=True)
            velocity = vehicle.get_velocity()
            speed_kmh = 3.6 * (velocity.x**2 + velocity.y**2 + velocity.z**2)**0.5
            print(f"  📍 road_id={wp.road_id if wp else '?'} | "
                  f"speed={speed_kmh:.1f} km/h | "
                  f"pos=({vehicle.get_location().x:.1f}, {vehicle.get_location().y:.1f})")
            time.sleep(5)
    except KeyboardInterrupt:
        print("\nSuppression du véhicule HERO...")
        vehicle.destroy()
        print("Véhicule supprimé.")


if __name__ == '__main__':
    main()
