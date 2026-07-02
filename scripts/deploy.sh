#!/bin/bash
# ═══════════════════════════════════════════════════════════
#  Deploy Script — MIDOC Digital Twin → GKE
#  Builds, pushes, and deploys all microservices
#
#  Usage:
#    ./scripts/deploy.sh                    # Deploy all
#    ./scripts/deploy.sh carla-adapter      # Deploy one service
# ═══════════════════════════════════════════════════════════

set -e

# ── Configuration ──
PROJECT_ID="midoc-digital-twin"
REGION="europe-west1"
REGISTRY="${REGION}-docker.pkg.dev/${PROJECT_ID}/midoc"
TAG=$(git rev-parse --short HEAD 2>/dev/null || echo "latest")
NAMESPACE="midoc-dt"

# Services to deploy
ALL_SERVICES=("carla-adapter" "orchestrator-service" "gama-adapter" "dashboard-service")

# If a specific service is provided, deploy only that
if [ -n "$1" ]; then
  SERVICES=("$1")
else
  SERVICES=("${ALL_SERVICES[@]}")
fi

echo "═══════════════════════════════════════════════════════════"
echo "  MIDOC Digital Twin — Deploy to GKE"
echo "  Tag: ${TAG}"
echo "═══════════════════════════════════════════════════════════"

# ── Step 1: Authenticate Docker with Artifact Registry ──
echo ""
echo "▸ Authenticating Docker..."
gcloud auth configure-docker ${REGION}-docker.pkg.dev --quiet

# ── Step 2: Build and push images ──
for SERVICE in "${SERVICES[@]}"; do
  echo ""
  echo "▸ Building: ${SERVICE}..."
  docker build -t ${REGISTRY}/${SERVICE}:${TAG} \
               -t ${REGISTRY}/${SERVICE}:latest \
               ./${SERVICE}

  echo "  Pushing: ${SERVICE}..."
  docker push ${REGISTRY}/${SERVICE}:${TAG}
  docker push ${REGISTRY}/${SERVICE}:latest
  echo "  ✓ ${SERVICE} pushed"
done

# ── Step 3: Update K8s manifests with correct project ID ──
echo ""
echo "▸ Updating manifests..."
find k8s/ -name "*.yaml" -exec sed -i "s/PROJECT_ID/${PROJECT_ID}/g" {} +

# ── Step 4: Apply K8s manifests ──
echo ""
echo "▸ Deploying to GKE..."

echo "  Applying namespace & configmaps..."
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmaps/

echo "  Deploying infrastructure..."
kubectl apply -f k8s/infrastructure/

echo "  Waiting for Kafka..."
kubectl -n ${NAMESPACE} rollout status deployment/zookeeper --timeout=120s 2>/dev/null || true
kubectl -n ${NAMESPACE} rollout status deployment/kafka --timeout=180s 2>/dev/null || true

echo "  Deploying application services..."
kubectl apply -f k8s/services/

# ── Step 5: Trigger rolling update for specific services ──
for SERVICE in "${SERVICES[@]}"; do
  echo "  Rolling update: ${SERVICE}..."
  kubectl -n ${NAMESPACE} set image deployment/${SERVICE} \
    ${SERVICE}=${REGISTRY}/${SERVICE}:${TAG} 2>/dev/null || true
done

# ── Step 6: Verify ──
echo ""
echo "▸ Verifying deployment..."
sleep 5

echo ""
echo "  ── Deployments ──"
kubectl -n ${NAMESPACE} get deployments

echo ""
echo "  ── Pods ──"
kubectl -n ${NAMESPACE} get pods

echo ""
echo "  ── Services ──"
kubectl -n ${NAMESPACE} get services

echo ""
EXTERNAL_IP=$(kubectl -n ${NAMESPACE} get service dashboard-service -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null)
if [ -n "${EXTERNAL_IP}" ]; then
  echo "  ✅ Dashboard accessible at: http://${EXTERNAL_IP}"
else
  echo "  ⏳ Dashboard IP pending... run:"
  echo "     kubectl -n ${NAMESPACE} get service dashboard-service --watch"
fi

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  ✅ Deployment complete!"
echo "═══════════════════════════════════════════════════════════"
