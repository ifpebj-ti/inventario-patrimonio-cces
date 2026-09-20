resource "oci_core_vcn" "main" {
  compartment_id = var.compartment_ocid
  cidr_block     = var.vcn_cidr
  display_name   = "${var.project_name}-vcn"
  dns_label      = "inventarium"
}

resource "oci_core_internet_gateway" "main" {
  compartment_id = var.compartment_ocid
  display_name   = "${var.project_name}-internet-gateway"
  enabled        = true
  vcn_id         = oci_core_vcn.main.id
}

resource "oci_core_route_table" "public" {
  compartment_id = var.compartment_ocid
  display_name   = "${var.project_name}-public-route-table"
  vcn_id         = oci_core_vcn.main.id

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.main.id
  }
}

resource "oci_core_nat_gateway" "main" {
  compartment_id = var.compartment_ocid
  display_name   = "${var.project_name}-nat-gateway"
  vcn_id         = oci_core_vcn.main.id
}

resource "oci_core_route_table" "private" {
  compartment_id = var.compartment_ocid
  display_name   = "${var.project_name}-private-route-table"
  vcn_id         = oci_core_vcn.main.id

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_nat_gateway.main.id
  }
}

resource "oci_core_security_list" "subnet_baseline" {
  compartment_id = var.compartment_ocid
  display_name   = "${var.project_name}-subnet-baseline-security-list"
  vcn_id         = oci_core_vcn.main.id

  egress_security_rules {
    destination = "0.0.0.0/0"
    protocol    = "all"
  }
}

resource "oci_core_subnet" "app_public" {
  compartment_id             = var.compartment_ocid
  cidr_block                 = var.app_public_subnet_cidr
  display_name               = "${var.project_name}-app-public-subnet"
  dns_label                  = "apppublic"
  prohibit_public_ip_on_vnic = false
  route_table_id             = oci_core_route_table.public.id
  security_list_ids          = [oci_core_security_list.subnet_baseline.id]
  vcn_id                     = oci_core_vcn.main.id
}

resource "oci_core_subnet" "observability_private" {
  compartment_id             = var.compartment_ocid
  cidr_block                 = var.observability_private_subnet_cidr
  display_name               = "${var.project_name}-observability-private-subnet"
  dns_label                  = "obsprivate"
  prohibit_public_ip_on_vnic = true
  route_table_id             = oci_core_route_table.private.id
  security_list_ids          = [oci_core_security_list.subnet_baseline.id]
  vcn_id                     = oci_core_vcn.main.id
}
