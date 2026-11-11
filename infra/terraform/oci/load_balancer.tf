# Load Balancer flexivel de camada 7 na frente da VM da aplicacao (ADR 0009). Termina o TLS e e onde o
# WAF se liga (waf.tf). Fala HTTP simples com o Nginx na 9080 (so o NSG do LB alcanca essa porta) e
# confere a saude na 9081.
#
# O certificado NAO e gerenciado aqui: a chave privada Let's Encrypt nunca deve passar pelo Terraform nem
# pelo state. O push-lb-certificate.sh (na VM) cria o certificado no LB e troca o do listener HTTPS a cada
# renovacao. Por isso o listener HTTPS so nasce com lb_https_enabled = true (depois do 1o certificado) e o
# Terraform ignora mudancas no nome do certificado dele.

locals {
  # Portas da VM acessadas pelo LB: ACME e redirecionamento (80), trafego HTTPS decifrado (9080) e health (9081).
  lb_backend_ports = {
    http   = 80
    app    = 9080
    health = 9081
  }
}

resource "oci_core_public_ip" "lb" {
  compartment_id = var.compartment_ocid
  display_name   = "${var.project_name}-lb-public-ip"
  lifetime       = "RESERVED"
}

resource "oci_load_balancer_load_balancer" "main" {
  compartment_id             = var.compartment_ocid
  display_name               = "${var.project_name}-lb"
  shape                      = "flexible"
  is_private                 = false
  subnet_ids                 = [oci_core_subnet.lb_public.id]
  network_security_group_ids = [oci_core_network_security_group.lb.id]

  shape_details {
    minimum_bandwidth_in_mbps = var.lb_min_mbps
    maximum_bandwidth_in_mbps = var.lb_max_mbps
  }

  # IP estavel para o DNS: sobrevive a recriacao do LB.
  reserved_ips {
    id = oci_core_public_ip.lb.id
  }
}

# Porta 80: ACME (HTTP-01 do certbot na VM) e redirecionamento para HTTPS, feitos pelo Nginx.
resource "oci_load_balancer_backend_set" "http" {
  load_balancer_id = oci_load_balancer_load_balancer.main.id
  name             = "http"
  policy           = "ROUND_ROBIN"

  health_checker {
    protocol          = "HTTP"
    port              = local.lb_backend_ports.health
    url_path          = "/healthz"
    return_code       = 200
    interval_ms       = 10000
    timeout_in_millis = 3000
    retries           = 3
  }
}

# Trafego HTTPS ja decifrado pelo LB, na 9080 do Nginx.
resource "oci_load_balancer_backend_set" "app" {
  load_balancer_id = oci_load_balancer_load_balancer.main.id
  name             = "app"
  policy           = "ROUND_ROBIN"

  health_checker {
    protocol          = "HTTP"
    port              = local.lb_backend_ports.health
    url_path          = "/healthz"
    return_code       = 200
    interval_ms       = 10000
    timeout_in_millis = 3000
    retries           = 3
  }
}

resource "oci_load_balancer_backend" "http" {
  load_balancer_id = oci_load_balancer_load_balancer.main.id
  backendset_name  = oci_load_balancer_backend_set.http.name
  ip_address       = oci_core_instance.app.private_ip
  port             = local.lb_backend_ports.http
}

resource "oci_load_balancer_backend" "app" {
  load_balancer_id = oci_load_balancer_load_balancer.main.id
  backendset_name  = oci_load_balancer_backend_set.app.name
  ip_address       = oci_core_instance.app.private_ip
  port             = local.lb_backend_ports.app
}

resource "oci_load_balancer_listener" "http" {
  load_balancer_id         = oci_load_balancer_load_balancer.main.id
  name                     = "http"
  default_backend_set_name = oci_load_balancer_backend_set.http.name
  port                     = 80
  protocol                 = "HTTP"
}

resource "oci_load_balancer_listener" "https" {
  count = var.lb_https_enabled ? 1 : 0

  load_balancer_id         = oci_load_balancer_load_balancer.main.id
  name                     = "https"
  default_backend_set_name = oci_load_balancer_backend_set.app.name
  port                     = 443
  protocol                 = "HTTP"

  connection_configuration {
    idle_timeout_in_seconds = var.lb_idle_timeout_seconds
  }

  ssl_configuration {
    certificate_name        = var.lb_bootstrap_certificate_name
    cipher_suite_name       = var.lb_cipher_suite_name
    protocols               = ["TLSv1.2", "TLSv1.3"]
    server_order_preference = "ENABLED"
    verify_peer_certificate = false
  }

  lifecycle {
    # O push-lb-certificate.sh troca o certificado a cada renovacao: o Terraform nao deve desfazer isso.
    ignore_changes = [ssl_configuration[0].certificate_name]

    precondition {
      condition     = var.lb_bootstrap_certificate_name != ""
      error_message = "Defina lb_bootstrap_certificate_name (certificado ja enviado ao LB pelo push-lb-certificate.sh) antes de ligar lb_https_enabled."
    }
  }
}
