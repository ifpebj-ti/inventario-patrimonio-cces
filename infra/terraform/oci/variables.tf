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

variable "terraform_state_bucket_name" {
  description = "Nome do bucket OCI Object Storage que armazenara o Terraform state remoto."
  type        = string
  default     = "inventarium-terraform-state"
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
  description = "IPs/CIDRs IPv4 autorizados a abrir as ferramentas administrativas (UI do Portainer e Grafana). A restricao e aplicada pelo Nginx da VM da aplicacao; o NSG nao expoe mais a porta 9443."
  type        = list(string)

  validation {
    condition     = length(var.allowed_admin_cidrs) > 0 && alltrue([for c in var.allowed_admin_cidrs : can(cidrhost(c, 0))])
    error_message = "allowed_admin_cidrs precisa ter ao menos um CIDR IPv4 valido (ex.: 203.0.113.10/32)."
  }

  validation {
    condition     = !contains(var.allowed_admin_cidrs, "0.0.0.0/0")
    error_message = "allowed_admin_cidrs nao pode conter 0.0.0.0/0: o Portainer controla o Docker da VM."
  }
}

variable "acme_email" {
  description = "E-mail de contato da conta Let's Encrypt (avisos de expiracao e problemas de emissao)."
  type        = string

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.acme_email))
    error_message = "acme_email precisa ser um endereco de e-mail valido."
  }
}

variable "portainer_version" {
  description = "Tag da imagem portainer/portainer-ce usada na VM da aplicacao (fixa, nunca lts/latest)."
  type        = string
  default     = "2.39.5"

  validation {
    condition     = can(regex("^[0-9]+\\.[0-9]+\\.[0-9]+$", var.portainer_version))
    error_message = "portainer_version deve ser uma versao fixa no formato X.Y.Z."
  }
}

variable "backup_bucket_name" {
  description = "Nome do bucket privado do Object Storage que recebe os backups do Postgres."
  type        = string
  default     = "inventarium-backups"
}

variable "backup_retention_days" {
  description = "Dias de retencao dos backups no bucket (lifecycle policy)."
  type        = number
  default     = 30
}

variable "manage_backup_iam" {
  description = "Cria o dynamic group e as policies IAM que permitem a VM enviar backups e o bucket aplicar a retencao. Exige permissao de IAM na tenancy; use false se o usuario do Terraform nao tiver."
  type        = bool
  default     = true
}

variable "availability_domain_index" {
  description = "Indice do Availability Domain usado pelas VMs."
  type        = number
  default     = 0
}

variable "instance_operating_system" {
  description = "Sistema operacional usado para selecionar a imagem das VMs."
  type        = string
  default     = "Oracle Linux"
}

variable "instance_operating_system_version" {
  description = "Versao do sistema operacional usado para selecionar a imagem das VMs."
  type        = string
  default     = "8"
}

variable "app_instance_shape" {
  description = "Shape da VM de aplicacao."
  type        = string
  default     = "VM.Standard.E5.Flex"
}

variable "app_instance_ocpus" {
  description = "OCPUs da VM de aplicacao quando o shape for flexivel."
  type        = number
  default     = 1
}

variable "app_instance_memory_gbs" {
  description = "Memoria em GB da VM de aplicacao quando o shape for flexivel."
  type        = number
  default     = 2
}

variable "observability_instance_shape" {
  description = "Shape da VM de observabilidade."
  type        = string
  default     = "VM.Standard.E5.Flex"
}

variable "observability_instance_ocpus" {
  description = "OCPUs da VM de observabilidade quando o shape for flexivel."
  type        = number
  default     = 1
}

variable "observability_instance_memory_gbs" {
  description = "Memoria em GB da VM de observabilidade quando o shape for flexivel."
  type        = number
  default     = 2
}
