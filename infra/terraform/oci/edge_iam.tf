# Permissao minima para a VM da aplicacao trocar o certificado do listener do Load Balancer com instance
# principal (push-lb-certificate.sh), sem chave de API na VM. Reaproveita o dynamic group da VM criado em
# backup.tf. Desligado por padrao (manage_edge_iam): exige permissao de IAM na tenancy.
#
# Usa o verbo "use" (nao "manage"): permite atualizar o LB e seus certificados e listeners, mas nao criar
# nem apagar load balancers. A condicao limita a este LB.
resource "oci_identity_policy" "edge" {
  count = var.manage_edge_iam ? 1 : 0

  compartment_id = var.compartment_ocid
  name           = "${var.project_name}-edge-certificate-rotation"
  description    = "A VM da aplicacao troca o certificado do listener HTTPS do Load Balancer do Inventarium."

  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.app[0].name} to use load-balancers in compartment id ${var.compartment_ocid} where target.loadbalancer.id = '${oci_load_balancer_load_balancer.main.id}'",
  ]

  lifecycle {
    precondition {
      condition     = var.manage_backup_iam
      error_message = "manage_edge_iam reaproveita o dynamic group criado com manage_backup_iam = true."
    }
  }
}
