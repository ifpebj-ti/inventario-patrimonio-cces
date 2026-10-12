# IP publico RESERVADO da VM da aplicacao. Fica associado a VNIC primaria e sobrevive a recriacao da
# VM, entao os registros DNS (inventariumifpe.me e subdominios) e a origem autorizada no Google
# continuam validos. A VM nasce sem IP publico (assign_public_ip = false); o cloud-init espera a
# conectividade antes de instalar pacotes.
data "oci_core_vnic_attachments" "app" {
  compartment_id = var.compartment_ocid
  instance_id    = oci_core_instance.app.id
}

data "oci_core_private_ips" "app" {
  vnic_id = data.oci_core_vnic_attachments.app.vnic_attachments[0].vnic_id
}

resource "oci_core_public_ip" "app" {
  compartment_id = var.compartment_ocid
  display_name   = "${var.project_name}-app-public-ip"
  lifetime       = "RESERVED"
  private_ip_id  = data.oci_core_private_ips.app.private_ips[0].id
}
