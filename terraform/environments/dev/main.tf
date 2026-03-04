# =============================================================================
# environments/dev/main.tf
# =============================================================================

locals {
  env = "dev"
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
