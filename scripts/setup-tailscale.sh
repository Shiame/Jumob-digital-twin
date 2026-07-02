#!/bin/bash
# ═══════════════════════════════════════════════════════════
#  Tailscale Setup — Hybrid Architecture Connectivity
#  Connects your on-premises lab (CARLA/GAMA) to GKE
#
#  This creates a secure VPN tunnel between:
#  - Your lab machine (running CARLA + GAMA simulators)
#  - Your GKE cluster (running the microservices)
#
#  The adapters in K8s will use the Tailscale IP to reach
#  the simulators on your local machine.
# ═══════════════════════════════════════════════════════════

set -e

echo "═══════════════════════════════════════════════════════════"
echo "  MIDOC Digital Twin — Tailscale VPN Setup"
echo "  Hybrid Edge-Cloud Architecture Connectivity"
echo "═══════════════════════════════════════════════════════════"

# ── Step 1: Install Tailscale ──
echo ""
echo "▸ Step 1: Installing Tailscale..."
if command -v tailscale &> /dev/null; then
  echo "  Tailscale already installed: $(tailscale version)"
else
  curl -fsSL https://tailscale.com/install.sh | sh
  echo "  ✓ Tailscale installed"
fi

# ── Step 2: Connect to Tailscale network ──
echo ""
echo "▸ Step 2: Connecting to Tailscale..."
echo "  This will open a browser for authentication."
echo "  Sign up for a free account at: https://login.tailscale.com"
echo ""
sudo tailscale up

# ── Step 3: Get your Tailscale IP ──
echo ""
echo "▸ Step 3: Your Tailscale IP:"
TAILSCALE_IP=$(tailscale ip -4)
echo "  ✓ IP: ${TAILSCALE_IP}"

# ── Step 4: Update K8s ConfigMap ──
echo ""
echo "▸ Step 4: Updating simulator-config ConfigMap..."
echo ""
echo "  Run these commands to update your K8s configuration:"
echo ""
echo "  # Update CARLA host"
echo "  kubectl -n midoc-dt create configmap simulator-config \\"
echo "    --from-literal=CARLA_HOST=${TAILSCALE_IP} \\"
echo "    --from-literal=CARLA_PORT=2000 \\"
echo "    --from-literal=GAMA_SERVER_URL=ws://${TAILSCALE_IP}:6868 \\"
echo "    --from-literal=GAMA_MODEL_PATH=/home/chaymae/gama-simulation05052025/gama-simulation/models/mobilitySimulator/Launcher.gaml \\"
echo "    --dry-run=client -o yaml | kubectl apply -f -"
echo ""

# ── Step 5: Verify connectivity ──
echo ""
echo "▸ Step 5: Verify connectivity"
echo ""
echo "  Make sure these ports are accessible on your machine:"
echo "  - CARLA: port 2000 (TCP)"
echo "  - GAMA:  port 6868 (WebSocket)"
echo ""
echo "  Test from another Tailscale device:"
echo "    nc -z ${TAILSCALE_IP} 2000 && echo 'CARLA OK' || echo 'CARLA FAIL'"
echo "    nc -z ${TAILSCALE_IP} 6868 && echo 'GAMA OK'  || echo 'GAMA FAIL'"
echo ""

echo "═══════════════════════════════════════════════════════════"
echo "  ✅ Tailscale Setup Complete!"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "  Your Tailscale IP: ${TAILSCALE_IP}"
echo ""
echo "  The adapters in GKE will connect to:"
echo "  - CARLA → ${TAILSCALE_IP}:2000"
echo "  - GAMA  → ws://${TAILSCALE_IP}:6868"
echo ""
echo "  This is the HYBRID EDGE-CLOUD architecture:"
echo "  ┌────────────────────┐      ┌────────────────────┐"
echo "  │   GKE (Cloud)      │      │  Lab (On-Premises) │"
echo "  │                    │      │                    │"
echo "  │  Adapters ─────────┼──VPN─┼──→ CARLA (GPU)    │"
echo "  │  Orchestrator      │      │  → GAMA           │"
echo "  │  Dashboard         │      │                    │"
echo "  │  Kafka             │      │                    │"
echo "  └────────────────────┘      └────────────────────┘"
echo ""
