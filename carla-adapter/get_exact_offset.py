import sys
import os
import glob
import math

try:
    sys.path.append(glob.glob('../carla/dist/carla-*%d.%d-%s.egg' % (
        sys.version_info.major,
        sys.version_info.minor,
        'win-amd64' if os.name == 'nt' else 'linux-x86_64'))[0])
except IndexError:
    pass

import carla

def main():
    client = carla.Client('localhost', 2000)
    client.set_timeout(5.0)
    world = client.get_world()
    carla_map = world.get_map()
    
    # Position trouvée par l'utilisateur
    target_loc = carla.Location(x=433.72, y=-634.73, z=44.18)
    
    geo = carla_map.transform_to_geolocation(target_loc)
    carla_lat = abs(geo.latitude)
    carla_lon = abs(geo.longitude)
    
    gama_lat = 43.5645
    gama_lon = 1.4685
    
    offset_lat = gama_lat - carla_lat
    offset_lon = gama_lon - carla_lon
    
    print(f"CARLA LAT: {carla_lat:.8f}")
    print(f"CARLA LON: {carla_lon:.8f}")
    print("---------------------------------")
    print(f"EXACT GAMA_VIS_LAT_OFFSET = {offset_lat:.8f}")
    print(f"EXACT GAMA_VIS_LON_OFFSET = {offset_lon:.8f}")

if __name__ == '__main__':
    main()
