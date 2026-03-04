output "vm_external_ip"        { value = module.network.vm_ip }
output "vm_name"               { value = module.compute.vm_name }
output "frontend_url"          { value = "http://${module.network.vm_ip}" }
output "grafana_url"           { value = "http://${module.network.vm_ip}:3000" }
output "artifact_registry_url" { value = "${var.region}-docker.pkg.dev/${var.project_id}/rag-app-prod" }
output "backup_bucket"         { value = module.database.backup_bucket_name }

output "github_actions_sa_key" {
  value     = base64decode(google_service_account_key.github_actions_key.private_key)
  sensitive = true
}