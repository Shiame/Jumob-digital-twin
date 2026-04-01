# GAMA Adapter

Ce microservice fait le pont entre la plateforme de simulation GAMA et Apache Kafka. Son rôle est de récupérer les événements d'une simulation GAMA en cours d'exécution et de publier ces données en temps réel sur Kafka pour alimenter l'architecture globale du Jumeau Numérique.

## Fonctionnement

L'adapter utilise une architecture réactive basée sur les WebSockets :
1. Au démarrage, il se connecte au serveur WebSocket natif de GAMA.
2. Il envoie automatiquement les commandes pour charger et exécuter le modèle `.gaml` ciblé.
3. Il écoute les données de télémétrie (positions des agents, vitesse, etc.) poussées par GAMA.
4. Il transforme JSON imbriqué brut envoyé par GAMA dans un format standardisé selon nos contrats Kafka.
5. Il publie les messages transformés sur le topic Kafka `gama-state`.

## Prérequis

- Plateforme GAMA avec le "Server Mode" activé sur le port par défaut (6868).
- Un broker Kafka tournant localement.

## Configuration

Les paramètres principaux sont dans `src/main/resources/application.properties`. 
Il est nécessaire d'adapter le chemin vers le fichier `.gaml` selon l'environnement de déploiement :

```properties
# Fichier modèle GAMA et nom de l'expérience à lancer
gama.model.path=/home/chaymae/Gama_Workspace/simple_test/models/Traffic and Pollution.gaml
gama.experiment.name=traffic

# Connexion GAMA Server
gama.server.url=ws://localhost:6868

# Configuration Kafka
spring.kafka.producer.bootstrap-servers=localhost:9092
kafka.topic.gama-state=gama-state
```

## Compilation & Exécution

1. Démarrer GAMA avec le mode serveur activé.
2. Démarrer Zookeeper et Kafka.
3. Compiler et lancer l'application Spring Boot

Le service expose des endpoints basiques de monitoring :
- Info sur le flux : `http://localhost:8081/api/status`
- Health check : `http://localhost:8081/api/health`

## Structure des messages envoyés (Topic: `gama-state`)

L'adapter convertit les sorties de simulation limitées de GAMA en événements `GamaStateEvent`. Exemple de data publiée sur Kafka à chaque step :

```json
{
  "eventId": "gama-a4e5d117",
  "eventType": "TICK_COMPLETED",
  "source": "GAMA",
  "timestamp": 1775033274103,
  "payload": {
    "cycle": 40,
    "nbPeople": 1000,
    "vehicles": [
      {
        "id": "people951",
        "x": 1356.5,
        "y": 772.88,
        "speed": 1.0,
        "heading": 271.44,
        "state": "notok"
      }
    ]
  }
}
```
