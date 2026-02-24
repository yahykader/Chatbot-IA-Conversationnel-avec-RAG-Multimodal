# =============================================================================
# modules/compute/main.tf
# =============================================================================

resource "google_compute_instance" "vm" {
  name         = var.vm_name
  machine_type = var.machine_type
  zone         = var.zone
  project      = var.project_id

  tags = ["http-server", "ssh-server", "grafana-server"]

  boot_disk {
    initialize_params {
      image = "debian-cloud/debian-12"
      size  = 50
      type  = "pd-ssd"
    }
  }

  network_interface {
    subnetwork = var.subnet_id

    access_config {
      nat_ip = var.vm_ip_address
    }
  }

  service_account {
    email  = var.sa_email
    scopes = ["cloud-platform"]
  }

  metadata_startup_script = <<-SCRIPT
    #!/bin/bash
    set -e

    # Docker
    curl -fsSL https://get.docker.com | sh
    usermod -aG docker debian
    systemctl enable docker
    systemctl start docker

    # Docker Compose plugin
    apt-get update -y
    apt-get install -y docker-compose-plugin

    # Artifact Registry auth
    gcloud auth configure-docker ${var.region}-docker.pkg.dev --quiet

    # Répertoires app
    mkdir -p /opt/rag-app/prometheus
    mkdir -p /opt/rag-app/grafana/provisioning
    chown -R debian:debian /opt/rag-app

    echo "[${var.env}] VM ready $(date)" >> /var/log/startup-script.log
  SCRIPT

  metadata = {
    enable-oslogin = "TRUE"
  }
}
