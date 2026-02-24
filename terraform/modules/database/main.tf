# =============================================================================
# modules/database/main.tf
# pgvector tourne sur la VM via Docker — pas de Cloud SQL
# Ce module gère uniquement le bucket GCS pour les backups PostgreSQL
# =============================================================================

resource "google_storage_bucket" "db_backups" {
  name          = "rag-db-backups-${var.env}-${var.project_id}"
  location      = var.region
  project       = var.project_id
  force_destroy = var.env == "dev" ? true : false

  versioning {
    enabled = true
  }

  lifecycle_rule {
    condition {
      age = var.env == "prod" ? 30 : 7
    }
    action {
      type = "Delete"
    }
  }
}
