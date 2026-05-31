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
            
            if target:
                print(f"Provocation d'une collision avec {target.type_id}...")
                hero.set_transform(target.get_transform())
                print("Téléportation réussie !")
            else:
                print(" Pas d'autre véhicule pour la collision.")
        else:
            print(" HERO non trouvé sur ce port.")
            
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
