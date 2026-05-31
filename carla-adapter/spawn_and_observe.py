"""
=======================================================
 CARLA Co-Simulation PROOF — Spawn + Drive + React
=======================================================
Script auto-suffisant qui fait TOUT :
  1. Tente de spawner un véhicule Tesla (hero) au plus
     proche des coordonnées absolues de GAMA (Rond-point)
  2. Le conduit manuellement en ligne droite à 8 km/h
  3. Écoute DIRECTEMENT le topic Kafka 'carla-commands'
  4. Quand l'orchestrateur envoie SET_SPEED_LIMIT →
     réduit la vitesse IMMÉDIATEMENT et visiblement

USAGE:
  python spawn_and_observe.py

IMPORTANT: Ce script remplace manual_control.py.
           L'affichage des piétons GAMA est géré par le 
           vrai CARLA Adapter (main.py).
"""

import carla
import time
import math
import json
import threading
import sys

# ============================================================
# CONFIG
# ============================================================
CARLA_HOST = 'localhost'
CARLA_PORT = 2000
KAFKA_BROKER = 'localhost:9092'
COMMANDS_TOPIC = 'carla-commands'

INITIAL_SPEED = 8.0   # km/h — vitesse initiale du véhicule

# ============================================================
# CONFIGURATION DE SPAWN
# ============================================================
# Au lieu d'utiliser le GPS (qui a un problème de projection), 
# on utilise les coordonnées exactes 3D locales de CARLA que vous avez trouvées !
TARGET_X = 433.72
TARGET_Y = -634.73
TARGET_Z = 45.0  # Un peu plus haut que 44.18 pour éviter les collisions

# ============================================================
# ÉTAT PARTAGÉ (thread-safe)
# ============================================================
_speed_lock = threading.Lock()
_target_speed = INITIAL_SPEED
_command_received = False
_last_reason = ""

def set_target_speed(speed_kmh, reason=""):
    global _target_speed, _command_received, _last_reason
    with _speed_lock:
        _target_speed = speed_kmh
        _command_received = True
        _last_reason = reason

def get_target_speed():
    with _speed_lock:
        return _target_speed

def was_command_received():
    global _command_received
    with _speed_lock:
        val = _command_received
        _command_received = False
        return val

# ============================================================
# KAFKA CONSUMER — carla-commands
# ============================================================
def start_kafka_commands_consumer():
    """Écoute 'carla-commands' et met à jour la vitesse cible."""
    try:
        from confluent_kafka import Consumer, KafkaError

        conf = {
            'bootstrap.servers': KAFKA_BROKER,
            'group.id': f'spawn-cmds-{int(time.time())}',
            'auto.offset.reset': 'latest',
        }
        consumer = Consumer(conf)
        consumer.subscribe([COMMANDS_TOPIC])
        print(f"✅ KAFKA COMMANDS connecté — écoute '{COMMANDS_TOPIC}'")

        while True:
            msg = consumer.poll(timeout=0.5)
            if msg is None:
                continue
            if msg.error():
                if msg.error().code() != KafkaError._PARTITION_EOF:
                    print(f"⚠️ Kafka commands error: {msg.error()}")
                continue

            try:
                raw = msg.value().decode('utf-8')
                command = json.loads(raw)
                cmd_type = command.get('commandType', '')

                if cmd_type == 'SET_SPEED_LIMIT':
                    speed = command.get('maxSpeedKmh', INITIAL_SPEED)
                    reason = command.get('reason', 'UNKNOWN')
                    peds = command.get('pedestrianCount', '?')

                    print()
                    print("=" * 60)
                    print(f"🚨🚨🚨 COMMANDE REÇUE DE L'ORCHESTRATEUR 🚨🚨🚨")
                    print(f"   Type:     {cmd_type}")
                    print(f"   Vitesse:  {speed} km/h")
                    print(f"   Raison:   {reason}")
                    print(f"   Piétons:  {peds}")
                    print("=" * 60)
                    print()

                    set_target_speed(speed, reason)

            except Exception as e:
                print(f"⚠️ Erreur parsing commande: {e}")

    except ImportError:
        print("⚠️ confluent_kafka non dispo pour carla-commands")
    except Exception as e:
        print(f"⚠️ Erreur Kafka commands: {e}")

# ============================================================
# FONCTIONS UTILITAIRES
# ============================================================
def get_speed(vehicle):
    """Vitesse du véhicule en km/h."""
    v = vehicle.get_velocity()
    return 3.6 * math.sqrt(v.x**2 + v.y**2 + v.z**2)

# ============================================================
# MAIN
# ============================================================
def main():
    print("=" * 60)
    print("  CARLA CO-SIMULATION PROOF")
    print("  Spawn + Drive + React (Pure Geo-Alignment)")
    print("=" * 60)

    # --- Connexion CARLA ---
    print(f"\n🔌 Connexion à CARLA ({CARLA_HOST}:{CARLA_PORT})...")
    client = carla.Client(CARLA_HOST, CARLA_PORT)
    client.set_timeout(10.0)
    world = client.get_world()
    carla_map = world.get_map()
    blueprint_library = world.get_blueprint_library()
    print("✅ Connecté à CARLA!")

    # --- Blueprint avec role_name = hero ---
    bp = blueprint_library.filter('vehicle.tesla.model3')[0]
    if bp.has_attribute('role_name'):
        bp.set_attribute('role_name', 'hero')

    # --- Nettoyer les véhicules existants ---
    existing = world.get_actors().filter('vehicle.*')
    for actor in existing:
        actor.destroy()
    print(f"🧹 {len(existing)} véhicules existants supprimés")

    # --- Spawn (Smart Absolute Alignment) ---
    spawn_points = carla_map.get_spawn_points()
    vehicle = None

    target_location = carla.Location(x=TARGET_X, y=TARGET_Y, z=TARGET_Z)
    
    print(f"🔍 Recherche de la route la plus proche de (X={TARGET_X}, Y={TARGET_Y})")
    
    # Trouver instantanément le point de route (waypoint) le plus proche
    wp = carla_map.get_waypoint(target_location, project_to_road=True, lane_type=carla.LaneType.Driving)
    
    if wp is not None:
        sp = wp.transform
        sp.location.z += 1.5  # Éviter collision avec le sol
        
        vehicle = world.try_spawn_actor(bp, sp)
        
        if vehicle is None:
            # Réessayer encore un peu plus haut
            sp.location.z += 1.0
            vehicle = world.try_spawn_actor(bp, sp)
            
        if vehicle is not None:
            print(f"🚗 Véhicule spawné avec succès sur la route !")
            print(f"   Position CARLA Local: x={sp.location.x:.1f}, y={sp.location.y:.1f}")

    if vehicle is None:
        print("❌ IMPOSSIBLE de spawner le véhicule!")
        sys.exit(1)

    # --- Configuration du véhicule ---
    vehicle.set_simulate_physics(True)
    
    # On active l'Autopilot pour que la voiture puisse tourner et suivre la route (le rond-point) !
    tm = client.get_trafficmanager()
    vehicle.set_autopilot(True, tm.get_port())
    tm.ignore_lights_percentage(vehicle, 100) # Ignorer les feux pour éviter qu'elle s'arrête pour rien
    
    # Forcer l'autopilot à rouler très lentement (INITIAL_SPEED)
    # CARLA roule à 30 km/h par défaut en ville. On veut rouler à 8 km/h, soit environ 73% plus lent.
    speed_reduction_pct = max(0.0, (30.0 - INITIAL_SPEED) / 30.0 * 100.0)
    tm.vehicle_percentage_speed_difference(vehicle, speed_reduction_pct)

    # --- Déplacer la caméra du simulateur (Spectator) vers le véhicule ---
    try:
        spectator = world.get_spectator()
        v_transform = vehicle.get_transform()
        # On place la caméra un peu en arrière et en hauteur
        cam_location = v_transform.location + carla.Location(z=15, x=-10)
        cam_rotation = carla.Rotation(pitch=-45, yaw=v_transform.rotation.yaw)
        spectator.set_transform(carla.Transform(cam_location, cam_rotation))
        print("🎥 Caméra du simulateur déplacée sur le véhicule !")
    except Exception as e:
        print(f"⚠️ Impossible de déplacer la caméra : {e}")

    # Petit frein initial pour stabiliser
    vehicle.apply_control(carla.VehicleControl(throttle=0.0, brake=1.0))
    time.sleep(0.3)

    print()
    print("🎮 MODE CONDUITE AUTOPILOT (La voiture tourne toute seule !)")
    print(f"   Vitesse initiale cible: {INITIAL_SPEED} km/h")
    print()

    # --- Démarrer les consumers Kafka en arrière-plan ---
    t1 = threading.Thread(target=start_kafka_commands_consumer, daemon=True)
    t1.start()

    print("⏳ En attente de carla-commands : pour recevoir SET_SPEED_LIMIT")
    print()

    # --- Boucle de conduite ---
    last_log_time = 0
    last_target = INITIAL_SPEED

    try:
        while True:
            current_speed = get_speed(vehicle)
            target = get_target_speed()

            # Si la vitesse cible a change, ajuster l'autopilot (garder autopilot ON!)
            if target != last_target:
                last_target = target
                # Calculer le pourcentage de reduction par rapport aux 30 km/h par defaut
                # Ex: target=2 km/h -> reduction = (30-2)/30*100 = 93%
                reduction_pct = max(0.0, (30.0 - target) / 30.0 * 100.0)
                tm.vehicle_percentage_speed_difference(vehicle, reduction_pct)
                print(f"\n>> VITESSE AJUSTEE: {target:.0f} km/h (reduction TM: {reduction_pct:.0f}%) | Raison: {_last_reason}")

            # Affichage propre: 1 ligne par seconde seulement
            now = time.time()
            if now - last_log_time >= 1.0:
                last_log_time = now
                mode = "RALENTI" if target < INITIAL_SPEED else "NORMAL"
                print(f"  [{mode}] Vitesse: {current_speed:5.1f}/{target:.0f} km/h")

            time.sleep(0.05)

    except KeyboardInterrupt:
        print("\nArret demande.")
    finally:
        if vehicle is not None:
            vehicle.destroy()
            print("Vehicule supprime. Au revoir!")

if __name__ == '__main__':
    main()
