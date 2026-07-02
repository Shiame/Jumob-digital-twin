import carla
import argparse

def diagnostic(host, port):
    print(f"🔗 Tentative de connexion sur {host}:{port}...")
    try:
        client = carla.Client(host, port)
        client.set_timeout(5.0)
        world = client.get_world()
        map_name = world.get_map().name
        all_actors = world.get_actors()
        
        print(f" Carte active : {map_name}")
        print(f"Nombre total d'acteurs : {len(all_actors)}")
        
        vehicles = all_actors.filter('vehicle.*')
        print(f" Nombre de véhicules : {len(vehicles)}")
        
        hero = None
        for v in vehicles:
            if 'tesla.model3' in v.type_id or v.attributes.get('role_name') == 'hero':
                hero = v
                print(f" HERO TROUVÉ ! ID: {v.id}")
                break
        
        if hero:
            # Chercher une cible
            target = None
            for v in vehicles:
                if v.id != hero.id:
                    target = v
                    break
            
            if not target:
                print(" Pas d'autre véhicule trouvé. Création d'une cible (Dummy) juste devant le Hero...")
                bp_lib = world.get_blueprint_library()
                dummy_bp = bp_lib.filter('vehicle.audi.tt')[0]
                
                # Placer la cible 10 mètres devant le hero
                hero_transform = hero.get_transform()
                forward_vector = hero_transform.get_forward_vector()
                
                dummy_transform = carla.Transform(
                    hero_transform.location + carla.Location(x=forward_vector.x*10, y=forward_vector.y*10, z=0),
                    hero_transform.rotation
                )
                
                target = world.try_spawn_actor(dummy_bp, dummy_transform)
                if target:
                    print(f" Cible Dummy spawnée ! (ID: {target.id})")
                else:
                    print(" Impossible de spawner la cible Dummy. Téléportation du Hero dans le sol pour forcer le crash...")
                    hero_transform.location.z -= 2.0
                    hero.set_transform(hero_transform)
            
            if target:
                print(f"💥 Provocation d'une collision avec {target.type_id}...")
                # On téléporte le hero EXACTEMENT sur la cible
                hero.set_transform(target.get_transform())
                print("Téléportation réussie ! REGARDEZ LES LOGS GAMA ET DOCKER !")
        else:
            print(" HERO non trouvé. Veuillez lancer spawn_hero_vehicle.py d'abord.")
            
    except Exception as e:
        print(f"Erreur sur le port {port}: {e}")

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--host', default='localhost')
    parser.add_argument('--port', type=int, default=2000)
    args = parser.parse_args()
    
    # On teste le port demandé, puis quelques ports adjacents au cas où
    diagnostic(args.host, args.port)
    if args.port == 2000:
        print("\n--- Test alternatif (port 2002) ---")
        diagnostic(args.host, 2002)
