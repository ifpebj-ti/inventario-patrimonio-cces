locals {
  app_is_flexible_shape           = endswith(var.app_instance_shape, ".Flex")
  observability_is_flexible_shape = endswith(var.observability_instance_shape, ".Flex")

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

  app_admin_ingress_rules = [
    for cidr in var.allowed_admin_cidrs : {
      cidr = cidr
      port = 9443
    }
  ]
}
