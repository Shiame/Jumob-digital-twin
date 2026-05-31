import carla
import time

def main():
    client = carla.Client('localhost', 2000)
    client.set_timeout(5.0)
    world = client.get_world()
    carla_map = world.get_map()
    
    spectator = world.get_spectator()
    
    print("🎥 Affichage de la position de la caméra (Spectator)...")
    print("Volez dans le simulateur jusqu'au rond-point et notez ces coordonnées :")
    print("-" * 60)
    
    try:
        while True:
            transform = spectator.get_transform()
            loc = transform.location
            try:
                geo = carla_map.transform_to_geolocation(loc)
                print(f"📍 Local 3D: X={loc.x:8.2f}, Y={loc.y:8.2f}, Z={loc.z:8.2f} | 🌍 GPS Brut CARLA: Lat={geo.latitude:.6f}, Lon={geo.longitude:.6f}", end='\r')
            except:
                pass
            time.sleep(0.5)
    except KeyboardInterrupt:
        print("\nTerminé.")

if __name__ == '__main__':
    main()
