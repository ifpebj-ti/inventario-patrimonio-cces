output "availability_domains" {
  description = "Availability Domains visiveis para a tenancy/regiao configurada."
  value       = data.oci_identity_availability_domains.current.availability_domains[*].name
}
