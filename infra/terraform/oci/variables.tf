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
  description = "CIDRs autorizados a acessar HTTP e HTTPS DIRETO na VM da aplicacao (sem passar pelo Load Balancer e pelo WAF). Com o Load Balancer em producao use [] para a VM aceitar 80/443 so do NSG do LB (ADR 0009)."
  type        = list(string)
  default     = []
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
  default     = 2
}

variable "observability_instance_memory_gbs" {
  description = "Memoria em GB da VM de observabilidade quando o shape for flexivel."
  type        = number
  default     = 8
}

variable "lb_public_subnet_cidr" {
  description = "CIDR da subnet publica dedicada ao Load Balancer (ADR 0009). Precisa estar dentro da VCN e nao sobrepor as subnets da aplicacao e da observabilidade."
  type        = string
  default     = "10.42.0.128/27"

  validation {
    condition     = can(cidrhost(var.lb_public_subnet_cidr, 0))
    error_message = "lb_public_subnet_cidr precisa ser um CIDR IPv4 valido (ex.: 10.42.0.128/27)."
  }

  # O Terraform nao tem cidrcontains: um endereco X esta em um prefixo P quando X com o tamanho de P,
  # normalizado por cidrsubnet, vira o proprio P.
  validation {
    condition = alltrue([
      for ip in [cidrhost(var.lb_public_subnet_cidr, 0), cidrhost(var.lb_public_subnet_cidr, -1)] :
      cidrsubnet("${ip}/${split("/", var.vcn_cidr)[1]}", 0, 0) == cidrsubnet(var.vcn_cidr, 0, 0)
    ])
    error_message = "lb_public_subnet_cidr precisa estar dentro de vcn_cidr."
  }

  validation {
    condition = alltrue(flatten([
      for other in [var.app_public_subnet_cidr, var.observability_private_subnet_cidr] : [
        for ip in [cidrhost(var.lb_public_subnet_cidr, 0), cidrhost(var.lb_public_subnet_cidr, -1)] :
        cidrsubnet("${ip}/${split("/", other)[1]}", 0, 0) != cidrsubnet(other, 0, 0)
      ]
      ])) && alltrue(flatten([
      for other in [var.app_public_subnet_cidr, var.observability_private_subnet_cidr] : [
        cidrsubnet("${cidrhost(other, 0)}/${split("/", var.lb_public_subnet_cidr)[1]}", 0, 0) != cidrsubnet(var.lb_public_subnet_cidr, 0, 0)
      ]
    ]))
    error_message = "lb_public_subnet_cidr nao pode sobrepor as subnets da aplicacao e da observabilidade."
  }
}

variable "lb_min_mbps" {
  description = "Banda minima do Load Balancer flexivel, em Mbps (10 e o minimo da OCI e a faixa gratuita, a confirmar no Cost Estimator)."
  type        = number
  default     = 10

  validation {
    condition     = var.lb_min_mbps >= 10
    error_message = "lb_min_mbps precisa ser no minimo 10."
  }
}

variable "lb_max_mbps" {
  description = "Banda maxima do Load Balancer flexivel, em Mbps. Acima da faixa minima a banda e cobrada."
  type        = number
  default     = 10

  validation {
    condition     = var.lb_max_mbps >= var.lb_min_mbps
    error_message = "lb_max_mbps precisa ser maior ou igual a lb_min_mbps."
  }
}

variable "lb_idle_timeout_seconds" {
  description = "Idle timeout do listener HTTPS. Precisa cobrir o webhook do Portainer, que pode levar 300 s; o keepalive do Nginx (350 s) e maior que este valor."
  type        = number
  default     = 330

  validation {
    condition     = var.lb_idle_timeout_seconds >= 60 && var.lb_idle_timeout_seconds < 350
    error_message = "lb_idle_timeout_seconds deve ficar entre 60 e 349, abaixo do keepalive_timeout do Nginx (350 s), para o LB nao reutilizar conexao ja fechada."
  }
}

variable "lb_cipher_suite_name" {
  description = "Suite de cifras do listener HTTPS (nome predefinido da OCI, com HTTP/2 e TLS 1.2 e 1.3). Confirme o nome no plano; a lista esta em Cipher Suites for Load Balancers."
  type        = string
  default     = "oci-default-http2-tls-12-13-ssl-cipher-suite-v1"
}

variable "lb_https_enabled" {
  description = "Cria o listener HTTPS do LB. So ligue depois de enviar o primeiro certificado ao LB (push-lb-certificate.sh) e informar o nome dele em lb_bootstrap_certificate_name: a chave privada nunca passa pelo Terraform nem pelo state."
  type        = bool
  default     = false
}

variable "lb_bootstrap_certificate_name" {
  description = "Nome do certificado do LB (criado pelo push-lb-certificate.sh) usado na criacao do listener HTTPS. Depois disso o script troca o certificado do listener e o Terraform ignora a mudanca."
  type        = string
  default     = ""

  validation {
    condition     = var.lb_bootstrap_certificate_name == "" || can(regex("^[A-Za-z0-9_-]{1,255}$", var.lb_bootstrap_certificate_name))
    error_message = "lb_bootstrap_certificate_name deve ter so letras, numeros, hifen e sublinhado."
  }
}

variable "waf_mode" {
  description = "Modo do WAF: DETECTION so registra (acao CHECK) e BLOCK bloqueia (403). Comece em DETECTION, revise os logs e so entao passe para BLOCK."
  type        = string
  default     = "DETECTION"

  validation {
    condition     = contains(["DETECTION", "BLOCK"], var.waf_mode)
    error_message = "waf_mode deve ser DETECTION ou BLOCK."
  }
}

variable "waf_cors_origin" {
  description = "Origem do front enviada em Access-Control-Allow-Origin nas respostas de bloqueio do WAF (o WAF nao repete o Origin da requisicao). Sem isso o navegador esconde o 403 e o front mostra servico indisponivel."
  type        = string
  default     = "https://inventariumifpe.me"

  validation {
    condition     = can(regex("^https://[a-z0-9.-]+$", var.waf_cors_origin))
    error_message = "waf_cors_origin deve ser uma origem https sem barra final (ex.: https://inventariumifpe.me)."
  }
}

variable "waf_log_retention_days" {
  description = "Dias de retencao dos logs do WAF no OCI Logging (a OCI aceita multiplos de 30, de 30 a 180)."
  type        = number
  default     = 30

  validation {
    condition     = contains([30, 60, 90, 120, 150, 180], var.waf_log_retention_days)
    error_message = "waf_log_retention_days deve ser 30, 60, 90, 120, 150 ou 180."
  }
}

variable "manage_edge_iam" {
  description = "Cria a policy IAM que deixa a VM da aplicacao (dynamic group do backup) trocar o certificado do listener do LB com instance principal. Exige manage_backup_iam = true (reaproveita o dynamic group) e permissao de IAM na tenancy."
  type        = bool
  default     = false
}

variable "waf_rate_limit_per_minute" {
  description = "Limite geral de requisicoes por minuto por IP no WAF (acima disso, acao check em DETECTION e 429 em BLOCK). O Nginx continua com os limites finos (login, Grafana)."
  type        = number
  default     = 1800

  validation {
    condition     = var.waf_rate_limit_per_minute >= 60
    error_message = "waf_rate_limit_per_minute precisa ser no minimo 60."
  }
}
