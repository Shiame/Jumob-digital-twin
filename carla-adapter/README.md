# 🚗 CARLA Adapter — Microservice Python

> **Pont entre le simulateur 3D CARLA et l'architecture de Jumeau Numérique via Apache Kafka**

Ce microservice fait partie du projet **MIDOC Digital Twin** (Mobilité Intelligente et Durable en Occitanie). Il est l'équivalent Python de l'Adaptateur GAMA (Spring Boot) : il connecte le simulateur **CARLA** à l'infrastructure de données **Kafka** en temps réel.

---

## 📐 Architecture

```
                             ┌─────────────────────────┐
                             │      CARLA Simulator     │
                             │  (Unreal Engine / GPU)   │
                             │      Port 2000           │
                             └────────────┬────────────┘
                                          │ Python API
                                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    CARLA ADAPTER (ce microservice)           │
│                                                             │
│  ┌──────────────┐   ┌──────────────────┐   ┌────────────┐  │
│  │   Connector   │──▶│  DataTransformer  │──▶│   Kafka    │  │
│  │ (Real / Mock) │   │  (Raw → Event)   │   │  Producer  │  │
│  └──────────────┘   └──────────────────┘   └────────────┘  │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  FastAPI Server (:8084)                              │   │
│  │  GET /api/health   GET /api/status                   │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                                          │
                                          ▼ JSON Events
                             ┌─────────────────────────┐
                             │     Apache Kafka         │
                             │   topic: carla-state     │
                             └─────────────────────────┘
```

---

## 📁 Structure du projet

```
carla-adapter/
├── main.py                    # Point d'entrée de l'application
├── config.py                  # Configuration centralisée (env vars / .env)
├── models/
│   └── events.py              # DTOs Pydantic (CarlaStateEvent, VehicleState)
├── services/
│   ├── carla_connector.py     # Connexion CARLA (réel + mock + factory)
│   ├── data_transformer.py    # Transformation des données brutes → événements
│   └── kafka_producer.py      # Publication dans Kafka
├── api/
│   └── health.py              # Endpoints REST (health, status)
├── requirements.txt           # Dépendances Python
├── Dockerfile                 # Image Docker
├── .env                       # Variables d'environnement (mock par défaut)
├── .env.example               # Template des variables d'environnement
├── slurm_carla.sh             # Script SLURM pour OcciData
└── README.md                  # Cette documentation
```

---

## 🔧 Configuration

Toutes les variables sont configurables via le fichier `.env` ou des variables d'environnement :

| Variable | Default | Description |
|---|---|---|
| `CARLA_HOST` | `127.0.0.1` | Adresse du serveur CARLA |
| `CARLA_PORT` | `2000` | Port du serveur CARLA |
| `CARLA_TIMEOUT` | `10.0` | Timeout de connexion (secondes) |
| `KAFKA_BROKER` | `localhost:9092` | Adresse du broker Kafka |
| `KAFKA_TOPIC` | `carla-state` | Topic Kafka de publication |
| `TICK_INTERVAL` | `0.1` | Intervalle entre les ticks (10 Hz) |
| `NUM_MOCK_VEHICLES` | `8` | Nombre de véhicules simulés en mock |
| `MOCK_MODE` | `true` | Mode mock (pas besoin de GPU/CARLA) |
| `API_HOST` | `0.0.0.0` | Hôte du serveur FastAPI |
| `API_PORT` | `8084` | Port du serveur FastAPI |

---

## 🚀 Démarrage rapide

### Mode Mock (sans CARLA, sans GPU — sur ton PC Dell)

```bash
cd carla-adapter/

# Installer les dépendances
pip install -r requirements.txt

# Lancer en mode mock
MOCK_MODE=true python main.py
```

Le mock génère 8 véhicules virtuels qui bougent de manière réaliste et publie les données dans Kafka à 10 Hz.

### Mode Réel (avec CARLA — sur OcciData)

```bash
# Sur le serveur OcciData, soumettre le job SLURM :
sbatch slurm_carla.sh

# Ou manuellement (si accès interactif GPU) :
MOCK_MODE=false CARLA_HOST=127.0.0.1 python main.py
```

### Docker

```bash
# Build
docker build -t carla-adapter .

# Run (mock mode)
docker run -p 8084:8084 -e MOCK_MODE=true carla-adapter
```

---

## 📊 Endpoints REST (Monitoring)

Le serveur FastAPI démarre automatiquement sur le port **8084**.

### `GET /api/health`
```json
{
  "status": "UP",
  "carlaConnection": "CONNECTED",
  "kafkaConnection": "CONNECTED"
}
```

### `GET /api/status`
```json
{
  "service": "carla-adapter",
  "mockMode": true,
  "carlaConnection": true,
  "carlaMap": "MockTown_Toulouse",
  "kafkaConnected": true,
  "messagesPublished": 1250,
  "lastError": null
}
```

---

## 📦 Format des événements Kafka (CarlaStateEvent)

Chaque tick de simulation génère un événement JSON publié dans le topic `carla-state` :

```json
{
  "eventId": "carla-a3b8d1f2",
  "eventType": "TICK_COMPLETED",
  "source": "CARLA",
  "timestamp": 1713714300000,
  "payload": {
    "tick_number": 42,
    "map_name": "Town01",
    "num_vehicles": 8,
    "vehicles": [
      {
        "id": 100,
        "type": "vehicle.tesla.model3",
        "x": 12.5432,
        "y": -8.3210,
        "z": 0.5,
        "pitch": 0.0,
        "yaw": 90.15,
        "roll": 0.0,
        "vx": 5.2341,
        "vy": 0.1234,
        "vz": 0.0,
        "speed_kmh": 18.85
      }
    ]
  }
}
```

> **Ce format est aligné avec `GamaStateEvent`** côté GAMA Adapter pour garantir l'interopérabilité.

---

## 🔄 Flux de données (Pipeline)

```
① Démarrage de l'application (main.py)
② Connexion au serveur CARLA (ou initialisation du mock)
③ Connexion au broker Kafka
④ Démarrage du serveur FastAPI (thread background)
⑤ Boucle principale (à chaque tick, toutes les 100ms) :
   ├─ Extraction de l'état des véhicules (positions, vitesses)
   ├─ Transformation en CarlaStateEvent (Pydantic DTO)
   ├─ Sérialisation JSON
   └─ Publication dans Kafka (topic: carla-state)
⑥ Arrêt propre sur SIGINT/SIGTERM (flush Kafka)
```

---

## 🖥️ Déploiement sur OcciData (SLURM)

1. **Transférer le code** vers OcciData :
   ```bash
   scp -r carla-adapter/ cbouazza@occidata-cluster.irit.fr:/projects/jumob/
   ```

2. **Créer le dossier de logs** :
   ```bash
   ssh cbouazza@occidata-cluster.irit.fr
   mkdir -p /projects/jumob/logs
   ```

3. **Soumettre le job** :
   ```bash
   cd /projects/jumob/carla-adapter
   sbatch slurm_carla.sh
   ```

4. **Surveiller** :
   ```bash
   squeue -u cbouazza                           # État du job
   tail -f /projects/jumob/logs/carla-adapter-*.out  # Logs en direct
   ```

---

## 🏗️ Stack technique

| Composant | Technologie |
|---|---|
| Langage | Python 3.10 |
| Validation des données | Pydantic v2 |
| Configuration | pydantic-settings |
| API REST | FastAPI + Uvicorn |
| Messaging | confluent-kafka |
| Conteneurisation | Docker |
| Déploiement HPC | SLURM (OcciData) |

---

## 🔗 Comparaison avec le GAMA Adapter

| Aspect | GAMA Adapter | CARLA Adapter |
|---|---|---|
| Langage | Java 17 | Python 3.10 |
| Framework | Spring Boot 3 | FastAPI |
| Communication simulateur | WebSocket | Python API (client lib) |
| DTO | `GamaStateEvent` (Jackson) | `CarlaStateEvent` (Pydantic) |
| Topic Kafka | `gama-state` | `carla-state` |
| Port API | 8082 | 8084 |
| Mode test | N/A | Mock mode (sans GPU) |
