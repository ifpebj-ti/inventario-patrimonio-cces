resource "oci_core_network_security_group" "app" {
  compartment_id = var.compartment_ocid
  display_name   = "${var.project_name}-app-nsg"
  vcn_id         = oci_core_vcn.main.id
}

resource "oci_core_network_security_group" "observability" {
  compartment_id = var.compartment_ocid
  display_name   = "${var.project_name}-observability-nsg"
  vcn_id         = oci_core_vcn.main.id
}

resource "oci_core_network_security_group_security_rule" "app_ssh_ingress" {
  for_each = { for rule in local.app_ssh_ingress_rules : "${rule.cidr}:${rule.port}" => rule }

  network_security_group_id = oci_core_network_security_group.app.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = each.value.cidr
  source_type               = "CIDR_BLOCK"

  tcp_options {
    destination_port_range {
      min = each.value.port
      max = each.value.port
    }
  }
}

resource "oci_core_network_security_group_security_rule" "app_web_ingress" {
  for_each = { for rule in local.app_web_ingress_rules : "${rule.cidr}:${rule.port}" => rule }

  network_security_group_id = oci_core_network_security_group.app.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = each.value.cidr
  source_type               = "CIDR_BLOCK"

  tcp_options {
    destination_port_range {
      min = each.value.port
      max = each.value.port
    }
  }
}

# Sem regra para a porta 9443: o Portainer escuta so em 127.0.0.1 e a UI e publicada pelo Nginx em
# portainer.<dominio>, com allowlist de IPs (allowed_admin_cidrs).

resource "oci_core_network_security_group_security_rule" "app_egress" {
  network_security_group_id = oci_core_network_security_group.app.id
  direction                 = "EGRESS"
  protocol                  = "all"
  destination               = "0.0.0.0/0"
  destination_type          = "CIDR_BLOCK"
}

resource "oci_core_network_security_group_security_rule" "observability_ssh_from_app_subnet" {
  network_security_group_id = oci_core_network_security_group.observability.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = var.app_public_subnet_cidr
  source_type               = "CIDR_BLOCK"

  tcp_options {
    destination_port_range {
      min = 22
      max = 22
    }
  }
}

resource "oci_core_network_security_group_security_rule" "observability_egress" {
  network_security_group_id = oci_core_network_security_group.observability.id
  direction                 = "EGRESS"
  protocol                  = "all"
  destination               = "0.0.0.0/0"
  destination_type          = "CIDR_BLOCK"
}

# Portas da stack de observabilidade (infra/observability), abertas SO para a subnet da aplicacao:
# o Nginx da VM da aplicacao alcanca Sonar e Grafana, e o Alloy empurra metricas, logs e traces.
# Nada aqui e exposto a internet: a VM de observabilidade nao tem IP publico.
resource "oci_core_network_security_group_security_rule" "observability_stack_from_app_subnet" {
  for_each = local.observability_stack_ports

  network_security_group_id = oci_core_network_security_group.observability.id
  description               = each.key
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = var.app_public_subnet_cidr
  source_type               = "CIDR_BLOCK"

  tcp_options {
    destination_port_range {
      min = each.value
      max = each.value
    }
  }
}

# ---- Load Balancer (ADR 0009) ------------------------------------------------------------------
# O LB e o unico ponto de entrada da internet. Ele recebe 80/443 de qualquer origem e fala com a VM so
# nas portas abaixo; a VM aceita essas portas somente do NSG do LB, entao o WAF nao pode ser contornado.
resource "oci_core_network_security_group" "lb" {
  compartment_id = var.compartment_ocid
  display_name   = "${var.project_name}-lb-nsg"
  vcn_id         = oci_core_vcn.main.id
}

resource "oci_core_network_security_group_security_rule" "lb_web_ingress" {
  for_each = toset(["80", "443"])

  network_security_group_id = oci_core_network_security_group.lb.id
  description               = "Internet para o Load Balancer (porta ${each.key})"
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = "0.0.0.0/0"
  source_type               = "CIDR_BLOCK"

  tcp_options {
    destination_port_range {
      min = tonumber(each.key)
      max = tonumber(each.key)
    }
  }
}

resource "oci_core_network_security_group_security_rule" "lb_to_app_egress" {
  for_each = local.lb_backend_ports

  network_security_group_id = oci_core_network_security_group.lb.id
  description               = "Load Balancer para a VM da aplicacao: ${each.key}"
  direction                 = "EGRESS"
  protocol                  = "6"
  destination               = oci_core_network_security_group.app.id
  destination_type          = "NETWORK_SECURITY_GROUP"

  tcp_options {
    destination_port_range {
      min = each.value
      max = each.value
    }
  }
}

resource "oci_core_network_security_group_security_rule" "app_from_lb" {
  for_each = local.lb_backend_ports

  network_security_group_id = oci_core_network_security_group.app.id
  description               = "Do Load Balancer: ${each.key}"
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = oci_core_network_security_group.lb.id
  source_type               = "NETWORK_SECURITY_GROUP"

  tcp_options {
    destination_port_range {
      min = each.value
      max = each.value
    }
  }
}
