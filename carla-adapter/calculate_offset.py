import carla

def main():
    try:
        client = carla.Client('localhost', 2000)
        client.set_timeout(5.0)
        world = client.get_world()
        carla_map = world.get_map()
        
        # Position locale 3D exacte du rond-point
        loc = carla.Location(x=433.72, y=-634.73, z=44.18)
        geo = carla_map.transform_to_geolocation(loc)
        
        carla_lat = abs(geo.latitude)
        carla_lon = abs(geo.longitude)
        
        gama_lat = 43.5645
        gama_lon = 1.4685
        
        offset_lat = gama_lat - carla_lat
        offset_lon = gama_lon - carla_lon
        
        print("\n" + "="*50)
        print("🎯 CALCULATEUR D'OFFSET GAMA-CARLA 🎯")
        print("="*50)
        print(f"CARLA Raw ABS : Lat={carla_lat:.8f}, Lon={carla_lon:.8f}")
        print(f"GAMA Cible    : Lat={gama_lat:.8f}, Lon={gama_lon:.8f}")
        print("-" * 50)
        print("✅ VOICI LES VALEURS À METTRE DANS config.py :")
        print(f"GAMA_VIS_LAT_OFFSET = {offset_lat:.8f}")
        print(f"GAMA_VIS_LON_OFFSET = {offset_lon:.8f}")
        print("=" * 50 + "\n")
        
    except Exception as e:
        print(f"Erreur : {e}")

if __name__ == '__main__':
    main()
