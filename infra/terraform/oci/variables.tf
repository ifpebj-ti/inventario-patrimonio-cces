variable "tenancy_ocid" {
  description = "OCID da tenancy OCI."
  type        = string
  sensitive   = true
}

variable "user_ocid" {
  description = "OCID do usuario OCI usado pelo Terraform."
  type        = string
  sensitive   = true
}

variable "fingerprint" {
  description = "Fingerprint da API key OCI."
  type        = string
  sensitive   = true
}

variable "private_key_path" {
  description = "Caminho local da chave privada da API OCI."
  type        = string
  sensitive   = true
}

variable "region" {
  description = "Regiao OCI onde os recursos serao provisionados."
  type        = string
}

variable "compartment_ocid" {
  description = "OCID do compartment inventarium."
  type        = string
  sensitive   = true
}

variable "project_name" {
  description = "Nome usado como prefixo dos recursos."
  type        = string
  default     = "inventarium"
}

variable "vcn_cidr" {
  description = "CIDR da VCN principal do Inventarium."
  type        = string
  default     = "10.42.0.0/24"
}

variable "app_public_subnet_cidr" {
  description = "CIDR da subnet publica da VM de aplicacao."
  type        = string
  default     = "10.42.0.0/26"
}

variable "observability_private_subnet_cidr" {
  description = "CIDR da subnet privada da VM de observabilidade."
  type        = string
  default     = "10.42.0.64/26"
}

variable "ssh_public_key" {
  description = "Chave publica SSH que sera adicionada nas VMs."
  type        = string
  sensitive   = true
}

variable "allowed_ssh_cidrs" {
  description = "CIDRs autorizados a acessar SSH."
  type        = list(string)
}

variable "allowed_web_cidrs" {
  description = "CIDRs autorizados a acessar HTTP e HTTPS."
  type        = list(string)
}

variable "allowed_admin_cidrs" {
  description = "CIDRs autorizados a acessar ferramentas administrativas."
  type        = list(string)
}
