#!/bin/bash
# ═══════════════════════════════════════════════════════════
#  GCP Setup Script — MIDOC Digital Twin
#  Run this ONCE to set up your Google Cloud environment
#
#  Prerequisites:
#  1. gcloud CLI installed (https://cloud.google.com/sdk/docs/install)
#  2. Logged in: gcloud auth login
#  3. Free trial $300 credits activated
# ═══════════════════════════════════════════════════════════

set -e  # Exit on error

# ── Configuration ──
PROJECT_ID="midoc-digital-twin"
REGION="europe-west1"
CLUSTER_NAME="midoc-cluster"
REPO_NAME="midoc"
SA_NAME="github-actions-deployer"

echo "═══════════════════════════════════════════════════════════"
echo "  MIDOC Digital Twin — GCP Setup"
echo "═══════════════════════════════════════════════════════════"

# ── Step 1: Create or select the project ──
echo ""
echo "▸ Step 1: Setting up project..."
gcloud projects create ${PROJECT_ID} --name="MIDOC Digital Twin" 2>/dev/null || true
gcloud config set project ${PROJECT_ID}

# ── Step 2: Link billing (required for GKE) ──
echo ""
echo "▸ Step 2: Checking billing..."
BILLING_ACCOUNT=$(gcloud billing accounts list --format="value(name)" | head -1)
if [ -n "$BILLING_ACCOUNT" ]; then
  gcloud billing projects link ${PROJECT_ID} --billing-account=${BILLING_ACCOUNT}
  echo "  Linked to billing account: ${BILLING_ACCOUNT}"
else
  echo "  ⚠ No billing account found. Please link one manually:"
  echo "    https://console.cloud.google.com/billing"
  exit 1
fi

# ── Step 3: Enable required APIs ──
echo ""
echo "▸ Step 3: Enabling APIs..."
gcloud services enable \
  container.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  --project=${PROJECT_ID}
echo "  ✓ Container, Artifact Registry, Cloud Build APIs enabled"

# ── Step 4: Create Artifact Registry repository ──
echo ""
echo "▸ Step 4: Creating Artifact Registry..."
gcloud artifacts repositories create ${REPO_NAME} \
  --repository-format=docker \
  --location=${REGION} \
  --description="MIDOC Digital Twin Docker images" \
  2>/dev/null || echo "  (Repository already exists)"
echo "  ✓ Registry: ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO_NAME}"

# ── Step 5: Create GKE Autopilot cluster ──
echo ""
echo "▸ Step 5: Creating GKE Autopilot cluster..."
echo "  This will take 5-10 minutes..."
gcloud container clusters create-auto ${CLUSTER_NAME} \
  --region=${REGION} \
  --project=${PROJECT_ID} \
  2>/dev/null || echo "  (Cluster already exists)"
echo "  ✓ Cluster: ${CLUSTER_NAME} in ${REGION}"

# ── Step 6: Get cluster credentials ──
echo ""
echo "▸ Step 6: Configuring kubectl..."
gcloud container clusters get-credentials ${CLUSTER_NAME} \
  --region=${REGION} \
  --project=${PROJECT_ID}
echo "  ✓ kubectl configured for ${CLUSTER_NAME}"

# ── Step 7: Create Service Account for GitHub Actions ──
echo ""
echo "▸ Step 7: Creating GitHub Actions Service Account..."
gcloud iam service-accounts create ${SA_NAME} \
  --display-name="GitHub Actions CI/CD" \
  --project=${PROJECT_ID} \
  2>/dev/null || echo "  (Service account already exists)"

SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

# Grant necessary roles
echo "  Granting roles..."
for ROLE in \
  roles/container.developer \
  roles/artifactregistry.writer \
  roles/storage.admin; do
  gcloud projects add-iam-policy-binding ${PROJECT_ID} \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="${ROLE}" \
    --quiet
done

# Generate key file for GitHub Actions secret
echo "  Generating key..."
gcloud iam service-accounts keys create gcp-sa-key.json \
  --iam-account=${SA_EMAIL} \
  --project=${PROJECT_ID}

echo "  ✓ Service Account key saved to: gcp-sa-key.json"

# ── Summary ──
echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  ✅ GCP Setup Complete!"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "  Project ID:     ${PROJECT_ID}"
echo "  Region:         ${REGION}"
echo "  GKE Cluster:    ${CLUSTER_NAME}"
echo "  Registry:       ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO_NAME}"
echo ""
echo "  ── Next Steps ──"
echo ""
echo "  1. Add GitHub Secrets:"
echo "     → GCP_PROJECT_ID = ${PROJECT_ID}"
echo "     → GCP_SA_KEY     = (contents of gcp-sa-key.json)"
echo ""
echo "     Go to: https://github.com/Shiame/Jumob-digital-twin/settings/secrets/actions"
echo ""
echo "  2. Install Tailscale on this machine:"
echo "     curl -fsSL https://tailscale.com/install.sh | sh"
echo "     sudo tailscale up"
echo ""
echo "  3. Deploy manually (first time):"
echo "     ./scripts/deploy.sh"
echo ""
echo "  ⚠ IMPORTANT: Delete gcp-sa-key.json after adding it to GitHub Secrets!"
echo "     rm gcp-sa-key.json"
echo ""
