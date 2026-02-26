variable "project_id" {
  type = string
}

variable "region" {
  type    = string
  default = "europe-west1"
}

variable "zone" {
  type    = string
  default = "europe-west1-b"
}

variable "vm_name" {
  type    = string
  default = "rag-vm-prod"
}

variable "machine_type" {
  type    = string
  default = "e2-standard-4" # 4 vCPU, 16GB
}

variable "openai_api_key" {
  type      = string
  sensitive = true
}