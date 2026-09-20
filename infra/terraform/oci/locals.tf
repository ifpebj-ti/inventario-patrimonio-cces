locals {
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
}
