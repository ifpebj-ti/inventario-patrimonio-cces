locals {
  app_is_flexible_shape           = endswith(var.app_instance_shape, ".Flex")
  observability_is_flexible_shape = endswith(var.observability_instance_shape, ".Flex")

  # IPs privados fixos: o Nginx da VM da aplicacao aponta para a observabilidade por IP, e a
  # observabilidade (Prometheus) aponta de volta para a aplicacao. Fixar evita dependencia circular
  # entre as duas VMs. Os 3 primeiros enderecos de cada subnet sao reservados pela OCI.
  app_private_ip           = cidrhost(var.app_public_subnet_cidr, 10)
  observability_private_ip = cidrhost(var.observability_private_subnet_cidr, 10)

  # Portas da stack de observabilidade (infra/observability/docker-compose.observability.yml).
  observability_stack_ports = {
    "SonarQube (Nginx da aplicacao)"    = 9000
    "Grafana (Nginx da aplicacao)"      = 3000
    "Prometheus remote write (Alloy)"   = 9090
    "Loki push (Alloy)"                 = 3100
    "Tempo OTLP gRPC (backend e Alloy)" = 4317
    "Tempo OTLP HTTP (backend e Alloy)" = 4318
  }

  app_ssh_ingress_rules = [
    for cidr in var.allowed_ssh_cidrs : {
      cidr = cidr
      port = 22
    }
  ]

  app_web_ingress_rules = flatten([
    for cidr in var.allowed_web_cidrs : [
      for port in [80, 443] : {
        cidr = cidr
        port = port
      }
    ]
  ])

  # Arquivos versionados que viajam no cloud-init da VM da aplicacao. Os de exemplo (generated.example)
  # ficam de fora: os reais sao gerados na VM. replace() remove CR caso o checkout tenha CRLF.
  nginx_src   = "${path.module}/../../nginx/app-vm"
  scripts_src = "${path.module}/../../scripts/app-vm"

  app_source_files = merge(
    {
      for f in fileset(local.nginx_src, "**") :
      "nginx/${f}" => replace(file("${local.nginx_src}/${f}"), "\r", "")
      if !startswith(f, "generated.example/")
    },
    {
      for f in fileset(local.scripts_src, "**") :
      "scripts/${f}" => replace(file("${local.scripts_src}/${f}"), "\r", "")
    },
  )

  # Configuracao nao secreta lida pelos scripts da VM (/etc/inventarium/edge.env).
  app_edge_env = join("\n", [
    "ACME_EMAIL=${var.acme_email}",
    "ADMIN_CIDRS=\"${join(" ", var.allowed_admin_cidrs)}\"",
    "OBSERVABILITY_IP=${local.observability_private_ip}",
    "BACKUP_BUCKET=${var.backup_bucket_name}",
    "OBJECT_STORAGE_NAMESPACE=${data.oci_objectstorage_namespace.current.namespace}",
    "PORTAINER_VERSION=${var.portainer_version}",
    "",
  ])

  app_cloud_init = templatefile("${path.module}/cloud-init-app.yaml.tftpl", {
    edge_env     = local.app_edge_env
    source_files = local.app_source_files
  })
}
