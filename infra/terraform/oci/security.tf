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

resource "oci_core_network_security_group_security_rule" "app_admin_ingress" {
  for_each = { for rule in local.app_admin_ingress_rules : "${rule.cidr}:${rule.port}" => rule }

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
