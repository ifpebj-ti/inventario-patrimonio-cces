# Backups do Postgres: bucket privado + retencao + permissao minima para a VM da aplicacao enviar os
# dumps com instance principal (sem chave de API na VM).
resource "oci_objectstorage_bucket" "backups" {
  compartment_id = var.compartment_ocid
  namespace      = data.oci_objectstorage_namespace.current.namespace
  name           = var.backup_bucket_name
  access_type    = "NoPublicAccess"
  storage_tier   = "Standard"
  versioning     = "Disabled"
}

resource "oci_objectstorage_object_lifecycle_policy" "backups" {
  namespace = data.oci_objectstorage_namespace.current.namespace
  bucket    = oci_objectstorage_bucket.backups.name

  rules {
    name        = "delete-old-postgres-backups"
    action      = "DELETE"
    is_enabled  = true
    target      = "objects"
    time_amount = var.backup_retention_days
    time_unit   = "DAYS"

    object_name_filter {
      inclusion_prefixes = ["postgres/"]
    }
  }

  # A lifecycle policy so funciona depois que o servico Object Storage pode apagar objetos.
  depends_on = [oci_identity_policy.backups]
}

resource "oci_identity_dynamic_group" "app" {
  count = var.manage_backup_iam ? 1 : 0

  compartment_id = var.tenancy_ocid
  name           = "${var.project_name}-app-instances"
  description    = "VM da aplicacao do Inventarium (instance principal para enviar backups)."
  matching_rule  = "ANY {instance.id = '${oci_core_instance.app.id}'}"
}

resource "oci_identity_policy" "backups" {
  count = var.manage_backup_iam ? 1 : 0

  compartment_id = var.compartment_ocid
  name           = "${var.project_name}-backups"
  description    = "Backups do Inventarium: a VM da aplicacao grava no bucket; o Object Storage aplica a retencao."

  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.app[0].name} to manage objects in compartment id ${var.compartment_ocid} where target.bucket.name = '${var.backup_bucket_name}'",
    "Allow service objectstorage-${var.region} to manage object-family in compartment id ${var.compartment_ocid}",
  ]
}
