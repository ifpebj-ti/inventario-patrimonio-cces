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
