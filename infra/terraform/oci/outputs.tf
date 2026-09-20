output "availability_domains" {
  description = "Availability Domains visiveis para a tenancy/regiao configurada."
  value       = data.oci_identity_availability_domains.current.availability_domains[*].name
}

output "vcn_id" {
  description = "OCID da VCN principal do Inventarium."
  value       = oci_core_vcn.main.id
}

output "app_public_subnet_id" {
  description = "OCID da subnet publica da aplicacao."
  value       = oci_core_subnet.app_public.id
}

output "observability_private_subnet_id" {
  description = "OCID da subnet privada de observabilidade."
  value       = oci_core_subnet.observability_private.id
}

output "app_nsg_id" {
  description = "OCID do NSG da VM de aplicacao."
  value       = oci_core_network_security_group.app.id
}

output "observability_nsg_id" {
  description = "OCID do NSG da VM de observabilidade."
  value       = oci_core_network_security_group.observability.id
}

output "object_storage_namespace" {
  description = "Namespace do OCI Object Storage usado pelo backend remoto."
  value       = data.oci_objectstorage_namespace.current.namespace
}

output "terraform_state_bucket_name" {
  description = "Nome do bucket criado para armazenar Terraform state remoto."
  value       = oci_objectstorage_bucket.terraform_state.name
}
