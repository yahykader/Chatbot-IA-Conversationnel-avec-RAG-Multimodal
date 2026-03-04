#!/bin/bash
# =============================================================================
# bootstrap.sh — Création complète du projet GCP + state Terraform
# Usage : ./bootstrap.sh <PROJECT_ID> [BILLING_ACCOUNT_ID]
#
# Ce script fait TOUT :
#   1. Crée le projet GCP
#   2. Lie le compte de facturation
#   3. Active les APIs nécessaires
#   4. Crée le bucket GCS pour le state Terraform
#   5. Crée le Service Account Terraform avec les droits admin
#   6. Génère la clé SA et configure gcloud
#   7. Vérifie que tout est prêt
# =============================================================================
set -e

# ─── Couleurs ────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log()     { echo -e "${GREEN}✅ $1${NC}"; }
warn()    { echo -e "${YELLOW}⚠️  $1${NC}"; }
error()   { echo -e "${RED}❌ $1${NC}"; exit 1; }
section() { echo -e "\n${BLUE}━━━ $1 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"; }

# ─── Arguments ───────────────────────────────────────────────────────────────
PROJECT_ID=$1
BILLING_ACCOUNT=$2

if [ -z "$PROJECT_ID" ]; then
  echo ""
  echo "Usage: ./bootstrap.sh <PROJECT_ID> [BILLING_ACCOUNT_ID]"
  echo ""
  echo "Exemples:"
  echo "  ./bootstrap.sh my-rag-app-prod"
  echo "  ./bootstrap.sh my-rag-app-prod 0X0X0X-0X0X0X-0X0X0X"
  echo ""
  echo "Pour trouver votre Billing Account ID :"
  echo "  gcloud billing accounts list"
  exit 1
fi

# ─── Config ──────────────────────────────────────────────────────────────────
BUCKET="terraform-state-${PROJECT_ID}"
REGION="europe-west1"
TF_SA_NAME="terraform-sa"
TF_SA_EMAIL="${TF_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
SA_KEY_FILE="terraform-sa-key.json"

echo ""
echo -e "${BLUE}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║        Bootstrap RAG App — GCP Setup             ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════╝${NC}"
echo ""
echo "  Project ID  : $PROJECT_ID"
echo "  Bucket      : $BUCKET"
echo "  Region      : $REGION"
echo "  SA Terraform: $TF_SA_EMAIL"
echo ""

# ─── Vérifier que gcloud est installé ────────────────────────────────────────
section "Vérification des prérequis"

command -v gcloud >/dev/null 2>&1 || error "gcloud CLI non trouvé. Installer : https://cloud.google.com/sdk/docs/install"
command -v terraform >/dev/null 2>&1 || warn "terraform non trouvé localement (OK si utilisé en CI/CD uniquement)"

# Vérifier que l'utilisateur est authentifié, sinon lancer auth login
CURRENT_ACCOUNT=$(gcloud auth list --filter=status:ACTIVE --format="value(account)" 2>/dev/null | head -1)
if [ -z "$CURRENT_ACCOUNT" ]; then
  warn "Non authentifié — ouverture du navigateur pour connexion Google..."
  gcloud auth login --no-launch-browser 2>/dev/null || gcloud auth login
  CURRENT_ACCOUNT=$(gcloud auth list --filter=status:ACTIVE --format="value(account)" 2>/dev/null | head -1)
  [ -z "$CURRENT_ACCOUNT" ] && error "Authentification échouée. Relancer le script."
fi
log "Authentifié en tant que : $CURRENT_ACCOUNT"

# ─── 1. Créer le projet GCP ───────────────────────────────────────────────────
section "Création du projet GCP"

if gcloud projects describe "$PROJECT_ID" >/dev/null 2>&1; then
  warn "Le projet $PROJECT_ID existe déjà — on continue"
else
  echo "Création du projet $PROJECT_ID..."
  gcloud projects create "$PROJECT_ID" \
    --name="RAG App" \
    --labels="env=rag-app,managed-by=terraform"
  log "Projet créé : $PROJECT_ID"
fi

# Définir comme projet par défaut
gcloud config set project "$PROJECT_ID"
log "Projet actif : $PROJECT_ID"

# ─── 2. Lier le compte de facturation ─────────────────────────────────────────
section "Compte de facturation"

if [ -n "$BILLING_ACCOUNT" ]; then
  gcloud billing projects link "$PROJECT_ID" \
    --billing-account="$BILLING_ACCOUNT"
  log "Billing account lié : $BILLING_ACCOUNT"
else
  # Récupérer automatiquement si un seul compte billing disponible
  BILLING_COUNT=$(gcloud billing accounts list --filter="open=true" --format="value(name)" 2>/dev/null | wc -l)

  if [ "$BILLING_COUNT" -eq 1 ]; then
    AUTO_BILLING=$(gcloud billing accounts list --filter="open=true" --format="value(name)" | head -1)
    gcloud billing projects link "$PROJECT_ID" --billing-account="$AUTO_BILLING"
    log "Billing account lié automatiquement : $AUTO_BILLING"
  else
    warn "Plusieurs comptes billing trouvés. Lier manuellement :"
    echo ""
    gcloud billing accounts list
    echo ""
    echo "  gcloud billing projects link $PROJECT_ID --billing-account=<ID>"
    echo ""
    warn "Continuons sans billing (certaines ressources peuvent échouer)"
  fi
fi

# ─── 3. Activer les APIs ──────────────────────────────────────────────────────
section "Activation des APIs GCP"

APIS=(
  "cloudresourcemanager.googleapis.com"
  "compute.googleapis.com"
  "artifactregistry.googleapis.com"
  "iam.googleapis.com"
  "iamcredentials.googleapis.com"
  "storage.googleapis.com"
  "storage-component.googleapis.com"
  "oslogin.googleapis.com"
)

echo "Activation de ${#APIS[@]} APIs (peut prendre 1-2 minutes)..."
gcloud services enable "${APIS[@]}" --project="$PROJECT_ID"
log "APIs activées"

# ─── 4. Créer le bucket GCS pour le state Terraform ──────────────────────────
section "Bucket GCS — State Terraform"

if gcloud storage buckets describe "gs://$BUCKET" >/dev/null 2>&1; then
  warn "Bucket gs://$BUCKET existe déjà — on continue"
else
  gcloud storage buckets create "gs://$BUCKET" \
    --project="$PROJECT_ID" \
    --location="$REGION" \
    --uniform-bucket-level-access
  log "Bucket créé : gs://$BUCKET"
fi

# Activer le versioning pour rollback
gcloud storage buckets update "gs://$BUCKET" --versioning
log "Versioning activé sur gs://$BUCKET"

# ─── 5. Créer le Service Account Terraform ───────────────────────────────────
section "Service Account Terraform"

if gcloud iam service-accounts describe "$TF_SA_EMAIL" --project="$PROJECT_ID" >/dev/null 2>&1; then
  warn "SA $TF_SA_EMAIL existe déjà — on continue"
else
  gcloud iam service-accounts create "$TF_SA_NAME" \
    --display-name="Terraform Bootstrap SA" \
    --project="$PROJECT_ID"
  log "Service Account créé : $TF_SA_EMAIL"
fi

# ─── 6. Assigner les rôles au SA Terraform ───────────────────────────────────
section "Rôles IAM pour le SA Terraform"

ROLES=(
  "roles/compute.admin"
  "roles/iam.serviceAccountAdmin"
  "roles/iam.serviceAccountKeyAdmin"
  "roles/iam.serviceAccountUser"
  "roles/artifactregistry.admin"
  "roles/storage.admin"
  "roles/resourcemanager.projectIamAdmin"
)

for ROLE in "${ROLES[@]}"; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:$TF_SA_EMAIL" \
    --role="$ROLE" \
    --quiet
  echo "  + $ROLE"
done
log "Rôles assignés"

# ─── 7. Générer la clé SA ────────────────────────────────────────────────────
section "Génération de la clé SA"

if [ -f "$SA_KEY_FILE" ]; then
  warn "Fichier $SA_KEY_FILE existe déjà — on le supprime et recrée"
  rm "$SA_KEY_FILE"
fi

gcloud iam service-accounts keys create "$SA_KEY_FILE" \
  --iam-account="$TF_SA_EMAIL" \
  --project="$PROJECT_ID"

log "Clé générée : $SA_KEY_FILE"

# Activer le SA pour les commandes terraform locales
gcloud auth activate-service-account --key-file="$SA_KEY_FILE"
log "gcloud activé avec le SA Terraform"

# ─── 8. Mettre à jour le bucket name dans les fichiers terraform ──────────────
section "Mise à jour des fichiers Terraform"

# Remplacer le bucket name dans versions.tf si les fichiers existent
if [ -d "terraform" ]; then
  for ENV in dev prod; do
    VERSIONS_FILE="terraform/environments/${ENV}/versions.tf"
    if [ -f "$VERSIONS_FILE" ]; then
      sed -i "s/bucket = \"terraform-state-rag-app\"/bucket = \"${BUCKET}\"/" "$VERSIONS_FILE"
      log "Mis à jour : $VERSIONS_FILE → bucket = $BUCKET"
    fi
  done
else
  warn "Dossier terraform/ non trouvé — penser à mettre à jour le bucket name dans versions.tf"
fi

# ─── Résumé final ─────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                   ✅ Bootstrap terminé !                     ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "  🪣 Bucket state  : gs://$BUCKET"
echo "  👤 SA Terraform  : $TF_SA_EMAIL"
echo "  🔑 Clé SA        : $SA_KEY_FILE"
echo ""
echo -e "${YELLOW}━━━ Prochaines étapes ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "  1. Terraform Dev :"
echo "     cd terraform/environments/dev"
echo "     terraform init"
echo "     terraform apply -var='project_id=$PROJECT_ID'"
echo "     terraform output -raw github_actions_sa_key > ../../sa-key-dev.json"
echo ""
echo "  2. Terraform Prod :"
echo "     cd terraform/environments/prod"
echo "     terraform init"
echo "     terraform apply -var='project_id=$PROJECT_ID'"
echo "     terraform output -raw github_actions_sa_key > ../../sa-key-prod.json"
echo ""
echo "  3. GitHub Secrets — copier le contenu de sa-key-prod.json dans GCP_SA_KEY"
echo "     cat sa-key-prod.json"
echo ""
echo -e "${RED}  ⚠️  IMPORTANT : supprimer les clés SA après usage !${NC}"
echo "     rm sa-key-dev.json sa-key-prod.json $SA_KEY_FILE"
echo ""
echo -e "${RED}  ⚠️  Ces fichiers sont dans .gitignore mais vérifiez quand même !${NC}"
echo ""
