# =============================================================================
# environments/prod/main.tf
# =============================================================================

locals {
  env = "prod"
}

# ── Service Account GitHub Actions ──────────────────────────────────────────
resource "google_service_account" "github_actions" {
  account_id   = "github-actions-sa-${local.env}"
  display_name = "GitHub Actions SA (${local.env})"
  project      = var.project_id
}

resource "google_project_iam_member" "artifact_registry_writer" {
  project = var.project_id
  role    = "roles/artifactregistry.writer"
  member  = "serviceAccount:${google_service_account.github_actions.email}"
}

resource "google_project_iam_member" "compute_admin" {
  project = var.project_id
  role    = "roles/compute.instanceAdmin.v1"
  member  = "serviceAccount:${google_service_account.github_actions.email}"
}

resource "google_project_iam_member" "sa_user" {
  project = var.project_id
  role    = "roles/iam.serviceAccountUser"
  member  = "serviceAccount:${google_service_account.github_actions.email}"
}

resource "google_service_account_key" "github_actions_key" {
  service_account_id = google_service_account.github_actions.name
}

# ── Artifact Registry ────────────────────────────────────────────────────────
resource "google_artifact_registry_repository" "rag_repo" {
  location      = var.region
  repository_id = "rag-app-${local.env}"
  format        = "DOCKER"
  project       = var.project_id
}

# ── Modules ──────────────────────────────────────────────────────────────────
module "network" {
  source     = "../../modules/network"
  project_id = var.project_id
  region     = var.region
  env        = local.env
}

module "compute" {
  source        = "../../modules/compute"
  project_id    = var.project_id
  zone          = var.zone
  region        = var.region
  env           = local.env
  vm_name       = var.vm_name
  machine_type  = var.machine_type
  subnet_id     = module.network.subnet_id
  vm_ip_address = module.network.vm_ip
  sa_email      = google_service_account.github_actions.email
}

module "database" {
  source     = "../../modules/database"
  project_id = var.project_id
  region     = var.region
  env        = local.env
}

# ── Activer API Cloud Run ─────────────────────────────────────────────────────
# Les services Cloud Run sont déployés via gcloud run deploy dans le job 4
resource "google_project_service" "cloud_run" {
  project            = var.project_id
  service            = "run.googleapis.com"
  disable_on_destroy = false
}

# ── Rôle Cloud Run pour le SA GitHub Actions ──────────────────────────────────
resource "google_project_iam_member" "cloud_run_admin" {
  project = var.project_id
  role    = "roles/run.admin"
  member  = "serviceAccount:${google_service_account.github_actions.email}"
}

resource "google_storage_bucket" "uploads" {
  name     = "rag-app-uploads-${var.project_id}"
  location = var.region
  project  = var.project_id
  uniform_bucket_level_access = true
}

# Donner accès au SA Cloud Run
resource "google_storage_bucket_iam_member" "uploads_access" {
  bucket = google_storage_bucket.uploads.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.github_actions.email}"
}

resource "google_project_iam_member" "cloudrun_ar_reader" {
  project = var.project_id
  role    = "roles/artifactregistry.reader"
  member  = "serviceAccount:${data.google_project.project.number}-compute@developer.gserviceaccount.com"
}

data "google_project" "project" {
  project_id = var.project_id
}