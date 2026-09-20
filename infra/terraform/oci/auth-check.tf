data "oci_identity_availability_domains" "current" {
  compartment_id = var.tenancy_ocid
}
