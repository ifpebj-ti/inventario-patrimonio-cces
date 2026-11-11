# WAF da OCI ligado ao Load Balancer (ADR 0009). Comeca em DETECTION: as regras usam a acao CHECK, que so
# registra no log e segue o processamento. Depois de alguns dias revisando os logs (falsos positivos), passe
# waf_mode para BLOCK: as mesmas regras passam a devolver 403 com o CORS do front.
#
# As capacidades de protecao (regras OWASP) vem do catalogo da propria OCI, na versao mais recente, e nao
# de chaves escritas aqui: o catalogo tem as regras colaborativas (com pesos e limite) e as individuais.
# Excecoes (webhook do Portainer, actuator/health, token Google) ficam para a etapa de bloqueio, depois de
# ver os falsos positivos reais nos logs: sem isso a sintaxe das condicoes seria chute.

data "oci_waf_protection_capabilities" "latest" {
  compartment_id    = var.compartment_ocid
  is_latest_version = [true]
  type              = "REQUEST_PROTECTION_CAPABILITY"
}

locals {
  # CHECK em deteccao, bloqueio (403) depois. O limite geral usa 429.
  waf_protect_action = var.waf_mode == "BLOCK" ? "block" : "check"
  waf_rate_action    = var.waf_mode == "BLOCK" ? "rate_limit" : "check"

  # Grupos colaborativos do catalogo da OCI: cada um pontua categorias de ataque e so age quando a soma passa
  # do limite (10), o que da pouco falso positivo. Sao a base contra SQL injection, XSS, path traversal/LFI e
  # execucao de comandos (RCE). Uma regra por familia: o log mostra qual disparou.
  waf_collaborative_prefixes = {
    "sql-injection"      = ["942"]
    "xss"                = ["941"]
    "path-traversal-lfi" = ["930"]
    "rce"                = ["932"]
  }

  # Regras individuais de alta precisao (cada uma age sozinha, entao so entram as que quase nao dao falso
  # positivo): restricao de metodos, scanners de vulnerabilidade pelo User-Agent, path traversal e Log4Shell.
  # Ficam de fora a familia de protocolo (920xxx: exige Accept, User-Agent e tipo de conteudo e quebraria o
  # smoke test do CI e clientes simples) e os User-Agent genericos (913101 e 913102, que pegariam o Node do CI).
  waf_standalone_keys = {
    "metodos-http"       = ["911100"]
    "scanners"           = ["913100", "913110", "913120"]
    "path-traversal-lfi" = ["930100", "930110", "930120", "930130"]
    "log4shell"          = ["944150", "944151", "944152"]
  }

  # Metodos aceitos pela regra 911100: os que a API e o front usam (CORS inclui OPTIONS).
  waf_allowed_methods = ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"]

  waf_catalog = data.oci_waf_protection_capabilities.latest.protection_capability_collection[0].items

  waf_rule_capabilities = {
    for name in toset(concat(keys(local.waf_collaborative_prefixes), keys(local.waf_standalone_keys))) : name => [
      for c in local.waf_catalog : c
      if(length(c.collaborative_weights) > 0 && anytrue([for p in lookup(local.waf_collaborative_prefixes, name, []) : startswith(c.key, p)])) ||
      contains(lookup(local.waf_standalone_keys, name, []), c.key)
    ]
  }

  # So regras que o catalogo realmente trouxe (uma chave que a OCI removesse nao quebra o plano).
  waf_active_rules = {
    for name, capabilities in local.waf_rule_capabilities : name => capabilities
    if length(capabilities) > 0
  }
}

resource "oci_waf_web_app_firewall_policy" "main" {
  compartment_id = var.compartment_ocid
  display_name   = "${var.project_name}-waf-policy"

  lifecycle {
    # Sem os grupos colaborativos de SQL injection e XSS o WAF nao protegeria o que o projeto precisa: se o
    # catalogo da OCI mudar as chaves, o plano falha em vez de criar uma politica vazia.
    precondition {
      condition     = alltrue([for name in ["sql-injection", "xss"] : contains(keys(local.waf_active_rules), name)])
      error_message = "O catalogo de protecao da OCI nao trouxe os grupos colaborativos de SQL injection (942) e XSS (941). Revise waf_collaborative_prefixes em waf.tf."
    }
  }

  actions {
    name = "allow"
    type = "ALLOW"
  }

  # Nao interrompe nada: registra o resultado da regra no log do WAF.
  actions {
    name = "check"
    type = "CHECK"
  }

  # O WAF nao repete o Origin da requisicao: o CORS e estatico. Sem ele o navegador esconde o 403 e o
  # front mostra "servico indisponivel" em vez de uma resposta clara.
  actions {
    name = "block"
    type = "RETURN_HTTP_RESPONSE"
    code = 403

    headers {
      name  = "Content-Type"
      value = "application/json"
    }
    headers {
      name  = "Access-Control-Allow-Origin"
      value = var.waf_cors_origin
    }

    body {
      type = "STATIC_TEXT"
      text = "{\"error\":\"blocked\"}"
    }
  }

  actions {
    name = "rate_limit"
    type = "RETURN_HTTP_RESPONSE"
    code = 429

    headers {
      name  = "Content-Type"
      value = "text/plain"
    }
    headers {
      name  = "Retry-After"
      value = "60"
    }
    headers {
      name  = "Access-Control-Allow-Origin"
      value = var.waf_cors_origin
    }
    headers {
      name  = "Access-Control-Expose-Headers"
      value = "Retry-After"
    }

    body {
      type = "STATIC_TEXT"
      text = "Too many requests"
    }
  }

  request_access_control {
    default_action_name = "allow"
  }

  request_rate_limiting {
    rules {
      name        = "limite-geral-por-ip"
      type        = "REQUEST_RATE_LIMITING"
      action_name = local.waf_rate_action

      configurations {
        period_in_seconds          = 60
        requests_limit             = var.waf_rate_limit_per_minute
        action_duration_in_seconds = 60
      }
    }
  }

  request_protection {
    # So os primeiros bytes do corpo sao inspecionados (limite da OCI: 8192 por tenancy). Sem uma acao de
    # excesso o restante passa sem ser barrado, o que evita derrubar o CSV de importacao e os envios ao Sonar.
    body_inspection_size_limit_in_bytes = 8192

    dynamic "rules" {
      for_each = local.waf_active_rules

      content {
        name                       = rules.key
        type                       = "PROTECTION"
        action_name                = local.waf_protect_action
        is_body_inspection_enabled = true

        dynamic "protection_capabilities" {
          for_each = rules.value

          content {
            key     = protection_capabilities.value.key
            version = protection_capabilities.value.version

            collaborative_action_threshold = length(protection_capabilities.value.collaborative_weights) > 0 ? protection_capabilities.value.collaborative_action_threshold : null

            dynamic "collaborative_weights" {
              for_each = protection_capabilities.value.collaborative_weights

              content {
                key    = collaborative_weights.value.key
                weight = collaborative_weights.value.weight
              }
            }
          }
        }

        dynamic "protection_capability_settings" {
          for_each = rules.key == "metodos-http" ? [local.waf_allowed_methods] : []

          content {
            allowed_http_methods = protection_capability_settings.value
          }
        }
      }
    }
  }
}

resource "oci_waf_web_app_firewall" "main" {
  compartment_id             = var.compartment_ocid
  display_name               = "${var.project_name}-waf"
  backend_type               = "LOAD_BALANCER"
  load_balancer_id           = oci_load_balancer_load_balancer.main.id
  web_app_firewall_policy_id = oci_waf_web_app_firewall_policy.main.id
}

# Logs do WAF (o que a regra viu e a acao tomada). E onde se revisam os falsos positivos antes do BLOCK.
resource "oci_logging_log_group" "waf" {
  compartment_id = var.compartment_ocid
  display_name   = "${var.project_name}-waf-logs"
  description    = "Logs do WAF do Load Balancer do Inventarium."
}

resource "oci_logging_log" "waf" {
  display_name       = "${var.project_name}-waf"
  log_group_id       = oci_logging_log_group.waf.id
  log_type           = "SERVICE"
  is_enabled         = true
  retention_duration = var.waf_log_retention_days

  configuration {
    compartment_id = var.compartment_ocid

    source {
      category    = "all"
      resource    = oci_waf_web_app_firewall.main.id
      service     = "waf"
      source_type = "OCISERVICE"
    }
  }
}
