terraform {
  required_version = ">= 1.6.0"

  backend "oci" {}

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 6.0"
    }
  }
}
