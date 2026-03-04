output "network_name" { value = google_compute_network.vpc.name }
output "subnet_id"    { value = google_compute_subnetwork.subnet.id }
output "vm_ip"        { value = google_compute_address.vm_ip.address }
output "vm_ip_name"   { value = google_compute_address.vm_ip.name }
