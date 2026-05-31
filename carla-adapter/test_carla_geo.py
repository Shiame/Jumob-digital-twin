import carla

def main():
    try:
        client = carla.Client('localhost', 2000)
        client.set_timeout(5.0)
        world = client.get_world()
        carla_map = world.get_map()
        
        waypoints = carla_map.generate_waypoints(10.0)
        if not waypoints:
            print("No waypoints found!")
            return
            
        lats = []
        lons = []
        for wp in waypoints:
            geo = carla_map.transform_to_geolocation(wp.transform.location)
            lats.append(geo.latitude)
            lons.append(geo.longitude)
            
        print(f"CARLA Map Geolocation Bounds:")
        print(f"Lat: {min(lats):.6f} to {max(lats):.6f}")
        print(f"Lon: {min(lons):.6f} to {max(lons):.6f}")
        
    except Exception as e:
        print(f"Error: {e}")

if __name__ == '__main__':
    main()
