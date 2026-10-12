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
    # O IP publico vem do oci_core_public_ip.app (RESERVED), que sobrevive a recriacao da VM.
    assign_public_ip = false
    display_name     = "${var.project_name}-app-vnic"
    hostname_label   = "app"
    nsg_ids          = [oci_core_network_security_group.app.id]
    private_ip       = local.app_private_ip
    subnet_id        = oci_core_subnet.app_public.id
  }

  metadata = {
    ssh_authorized_keys = var.ssh_public_key
    # gzip: o Nginx e os scripts versionados estouram o limite de 32 KB do user_data em texto puro.
    # O cloud-init descompacta automaticamente.
    user_data = base64gzip(local.app_cloud_init)
  }

  source_details {
    source_id   = data.oci_core_images.app.images[0].id
    source_type = "image"
  }

  lifecycle {
    # A VM guarda o volume do Postgres. Nginx, scripts e a imagem "mais recente" mudam com o tempo e
    # NAO podem recriar a VM: atualize o Nginx/scripts com infra/scripts/deploy-nginx.sh. Para
    # reprovisionar de proposito (perdendo o volume, depois de restaurar de backup), use
    # `terraform apply -replace=oci_core_instance.app`.
    ignore_changes = [source_details[0].source_id, metadata["user_data"]]
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
    private_ip       = local.observability_private_ip
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
