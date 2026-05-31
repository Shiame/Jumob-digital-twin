# ═══════════════════════════════════════════════════════════
#  Dockerfile — CARLA Adapter
# ═══════════════════════════════════════════════════════════
FROM python:3.10-slim

LABEL maintainer="Chaymae Bouazza"
LABEL description="CARLA Adapter microservice for the MIDOC Digital Twin"

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Expose API port
EXPOSE 8084

# Default: run in mock mode
ENV MOCK_MODE=true

CMD ["python", "main.py"]
