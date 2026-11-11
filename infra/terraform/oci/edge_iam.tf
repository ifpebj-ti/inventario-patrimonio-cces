# Permissao minima para a VM da aplicacao trocar o certificado do listener do Load Balancer com instance
# principal (push-lb-certificate.sh), sem chave de API na VM. Reaproveita o dynamic group da VM criado em
# backup.tf. Desligado por padrao (manage_edge_iam): exige permissao de IAM na tenancy.
#
# Usa o verbo "use" (nao "manage"): permite atualizar o LB e seus certificados e listeners, mas nao criar
# nem apagar load balancers.
#
# SEM condicao "where target.loadbalancer.id = ...": testado na VM real, com essa condicao a OCI responde
# 404 NotAuthorizedOrNotFound a toda chamada (a variavel nao vale para load balancers) e sem ela funciona.
# O custo e a VM poder atualizar qualquer LB do compartment, nao so este; hoje ha um unico LB (ADR 0009).
resource "oci_identity_policy" "edge" {
  count = var.manage_edge_iam ? 1 : 0

  compartment_id = var.compartment_ocid
  name           = "${var.project_name}-edge-certificate-rotation"
  description    = "A VM da aplicacao troca o certificado do listener HTTPS do Load Balancer do Inventarium."

  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.app[0].name} to use load-balancers in compartment id ${var.compartment_ocid}",
  ]

  lifecycle {
    precondition {
      condition     = var.manage_backup_iam
      error_message = "manage_edge_iam reaproveita o dynamic group criado com manage_backup_iam = true."
    }
  }
}
