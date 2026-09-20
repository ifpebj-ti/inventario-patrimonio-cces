data "oci_core_images" "app" {
  compartment_id           = var.compartment_ocid
  operating_system         = var.instance_operating_system
  operating_system_version = var.instance_operating_system_version
  shape                    = var.app_instance_shape
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

data "oci_core_images" "observability" {
  compartment_id           = var.compartment_ocid
  operating_system         = var.instance_operating_system
  operating_system_version = var.instance_operating_system_version
  shape                    = var.observability_instance_shape
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

resource "oci_core_instance" "app" {
  availability_domain = data.oci_identity_availability_domains.current.availability_domains[var.availability_domain_index].name
  compartment_id      = var.compartment_ocid
  display_name        = "${var.project_name}-app-vm"
  shape               = var.app_instance_shape

  dynamic "shape_config" {
    for_each = local.app_is_flexible_shape ? [1] : []

    content {
      ocpus         = var.app_instance_ocpus
      memory_in_gbs = var.app_instance_memory_gbs
    }
  }

  create_vnic_details {
    assign_public_ip = true
    display_name     = "${var.project_name}-app-vnic"
    hostname_label   = "app"
    nsg_ids          = [oci_core_network_security_group.app.id]
    subnet_id        = oci_core_subnet.app_public.id
  }

  metadata = {
    ssh_authorized_keys = var.ssh_public_key
    user_data           = base64encode(templatefile("${path.module}/cloud-init-app.yaml.tftpl", {}))
  }

  source_details {
    source_id   = data.oci_core_images.app.images[0].id
    source_type = "image"
  }
}

resource "oci_core_instance" "observability" {
  availability_domain = data.oci_identity_availability_domains.current.availability_domains[var.availability_domain_index].name
  compartment_id      = var.compartment_ocid
  display_name        = "${var.project_name}-observability-vm"
  shape               = var.observability_instance_shape

  dynamic "shape_config" {
    for_each = local.observability_is_flexible_shape ? [1] : []

    content {
      ocpus         = var.observability_instance_ocpus
      memory_in_gbs = var.observability_instance_memory_gbs
    }
  }

  create_vnic_details {
    assign_public_ip = false
    display_name     = "${var.project_name}-observability-vnic"
    hostname_label   = "observability"
    nsg_ids          = [oci_core_network_security_group.observability.id]
    subnet_id        = oci_core_subnet.observability_private.id
  }

  metadata = {
    ssh_authorized_keys = var.ssh_public_key
    user_data           = base64encode(templatefile("${path.module}/cloud-init-observability.yaml.tftpl", {}))
  }

  source_details {
    source_id   = data.oci_core_images.observability.images[0].id
    source_type = "image"
  }
}
