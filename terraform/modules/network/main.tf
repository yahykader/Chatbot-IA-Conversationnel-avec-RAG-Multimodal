# =============================================================================
# modules/network/main.tf
# =============================================================================

resource "google_compute_network" "vpc" {
  name                    = "rag-network-${var.env}"
  auto_create_subnetworks = false
  project                 = var.project_id
}

resource "google_compute_subnetwork" "subnet" {
  name          = "rag-subnet-${var.env}"
  ip_cidr_range = "10.0.0.0/24"
  region        = var.region
  network       = google_compute_network.vpc.id
  project       = var.project_id
}

resource "google_compute_address" "vm_ip" {
  name    = "rag-vm-ip-${var.env}"
  region  = var.region
  project = var.project_id
}

# Port 80 — Frontend public
resource "google_compute_firewall" "allow_http" {
  name    = "allow-http-${var.env}"
  network = google_compute_network.vpc.name
  project = var.project_id

  allow {
    protocol = "tcp"
    ports    = ["80"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["http-server"]
}

# Port 22 — SSH via IAP
resource "google_compute_firewall" "allow_ssh_iap" {
  name    = "allow-ssh-iap-${var.env}"
  network = google_compute_network.vpc.name
  project = var.project_id

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["35.235.240.0/20"]
  target_tags   = ["ssh-server"]
}

# Port 22 — SSH direct (GitHub Actions)
resource "google_compute_firewall" "allow_ssh_direct" {
  name    = "allow-ssh-direct-${var.env}"
  network = google_compute_network.vpc.name
  project = var.project_id

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["ssh-server"]
}

# Port 3000 — Grafana (restreint)
resource "google_compute_firewall" "allow_grafana" {
  name    = "allow-grafana-${var.env}"
  network = google_compute_network.vpc.name
  project = var.project_id

  allow {
    protocol = "tcp"
    ports    = ["3000"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["grafana-server"]
}

# Ports applicatifs — Backend, Redis Commander, Zipkin, Prometheus, Cadvisor + DB/Cache pour Cloud Run
resource "google_compute_firewall" "allow_app_ports" {
  name    = "allow-app-ports-${var.env}"
  network = google_compute_network.vpc.name
  project = var.project_id

  allow {
    protocol = "tcp"
    ports    = ["8081", "8090", "9090", "9411", "9093", "5432", "6379", "3310", "8082", "8080"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["http-server"]
}
