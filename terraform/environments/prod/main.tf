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
resource "google_project_service" "cloud_run" {
  project = var.project_id
  service = "run.googleapis.com"
  disable_on_destroy = false
}

# ── Rôle Cloud Run pour le SA GitHub Actions ──────────────────────────────────
resource "google_project_iam_member" "cloud_run_admin" {
  project = var.project_id
  role    = "roles/run.admin"
  member  = "serviceAccount:${google_service_account.github_actions.email}"
}

# ── Cloud Run Backend ─────────────────────────────────────────────────────────
resource "google_cloud_run_service" "backend" {
  name     = "rag-backend-${local.env}"
  location = var.region
  project  = var.project_id

  depends_on = [google_project_service.cloud_run]

  template {
    spec {
      containers {
        image = "${var.region}-docker.pkg.dev/${var.project_id}/rag-app-${local.env}/rag-backend:latest"

        env {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "docker"
        }
        env {
          name  = "PGVECTOR_HOST"
          value = module.network.vm_ip
        }
        env {
          name  = "SPRING_DATASOURCE_URL"
          value = "jdbc:postgresql://${module.network.vm_ip}:5432/vectordb"
        }
        env {
          name  = "SPRING_REDIS_HOST"
          value = module.network.vm_ip
        }
        env {
          name  = "CLAMAV_HOST"
          value = module.network.vm_ip
        }
        env {
          name  = "MANAGEMENT_ZIPKIN_TRACING_ENDPOINT"
          value = "http://${module.network.vm_ip}:9411/api/v2/spans"
        }
        env {
          name  = "IMAGES_STORAGE_PATH"
          value = "gs://rag-app-uploads-prod"  # bucket GCS au lieu du volume local
        }
        env {
          name  = "OPENAI_API_KEY"
          value = var.openai_api_key
        }

        resources {
          limits = {
            cpu    = "2"
            memory = "2Gi"
          }
        }
      }
    }
  }

  traffic {
    percent         = 100
    latest_revision = true
  }
}

# ── Cloud Run Frontend ────────────────────────────────────────────────────────
resource "google_cloud_run_service" "frontend" {
  name     = "rag-frontend-${local.env}"
  location = var.region
  project  = var.project_id

  depends_on = [google_project_service.cloud_run]

  template {
    spec {
      containers {
        image = "${var.region}-docker.pkg.dev/${var.project_id}/rag-app-${local.env}/rag-frontend:latest"

        resources {
          limits = {
            cpu    = "1"
            memory = "512Mi"
          }
        }
      }
    }
  }

  traffic {
    percent         = 100
    latest_revision = true
  }
}

# ── Accès public Cloud Run ────────────────────────────────────────────────────
resource "google_cloud_run_service_iam_member" "backend_public" {
  service  = google_cloud_run_service.backend.name
  location = var.region
  project  = var.project_id
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_service_iam_member" "frontend_public" {
  service  = google_cloud_run_service.frontend.name
  location = var.region
  project  = var.project_id
  role     = "roles/run.invoker"
  member   = "allUsers"
}
