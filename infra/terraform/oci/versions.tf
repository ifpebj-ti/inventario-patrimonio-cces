terraform {
  # O backend "oci" nativo do Terraform exige a versao 1.12 ou superior.
  required_version = ">= 1.12.0"

  backend "oci" {}

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 6.0"
    }
  }
}
