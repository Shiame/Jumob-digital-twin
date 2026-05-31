"""
CARLA Adapter — Centralized Configuration
==========================================
All settings are loaded from environment variables or a .env file.
Uses pydantic-settings for type-safe configuration with validation.
"""

from pydantic_settings import BaseSettings
from pydantic import Field


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    # --- CARLA Connection ---
    CARLA_HOST: str = Field(default="127.0.0.1", description="CARLA simulator host")
    CARLA_PORT: int = Field(default=2000, description="CARLA simulator port")
    CARLA_TIMEOUT: float = Field(default=10.0, description="CARLA connection timeout (seconds)")

    # --- Kafka ---
    KAFKA_BROKER: str = Field(default="localhost:9092", description="Kafka bootstrap server")
    KAFKA_TOPIC: str = Field(default="carla-state", description="Kafka topic for CARLA events")
    KAFKA_COMMANDS_TOPIC: str = Field(default="carla-commands", description="Kafka topic for receiving orchestrator commands")

    # --- Simulation ---
    TICK_INTERVAL: float = Field(default=0.1, description="Data extraction interval in seconds (10 Hz)")
    NUM_MOCK_VEHICLES: int = Field(default=8, description="Number of mock vehicles to simulate")

    # --- Modes ---
    MOCK_MODE: bool = Field(default=False, description="If True, generate fake data (no CARLA needed)")

    # --- GAMA Visualization ---
    KAFKA_GAMA_STATE_TOPIC: str = Field(default="gama-state", description="Kafka topic for incoming GAMA state events")
    GAMA_VIS_MAX_AGENTS: int = Field(default=20, description="Max GAMA agents to display per tick (avoid overloading CARLA)")
    GAMA_VIS_MARKER_LIFETIME: float = Field(default=0.5, description="Debug marker lifetime in seconds (refreshed every tick)")
    GAMA_VIS_Z_OFFSET: float = Field(default=1.5, description="Meters above road surface for marker visibility")
    GAMA_VIS_LAT_OFFSET: float = Field(default=0.0, description="Latitude offset to correct systematic geo-shift")
    GAMA_VIS_LON_OFFSET: float = Field(default=0.0, description="Longitude offset to correct systematic geo-shift")
    GAMA_VIS_ENABLED: bool = Field(default=True, description="Master switch for GAMA agent visualization")

    # --- API ---
    API_HOST: str = Field(default="0.0.0.0", description="FastAPI host")
    API_PORT: int = Field(default=8084, description="FastAPI port")

    model_config = {
        "env_file": ".env",
        "env_file_encoding": "utf-8",
        "case_sensitive": True,
    }


# Singleton instance
settings = Settings()
