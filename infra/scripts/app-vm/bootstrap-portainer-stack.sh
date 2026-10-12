#!/usr/bin/env bash
# Cria no Portainer a stack "inventarium" (GitOps) a partir do docker-compose.production.yml do
# repositorio, com atualizacao por webhook. Os segredos vem de um arquivo .env que voce envia por
# scp e que e apagado no fim; nunca passam pelo Terraform nem pelo user-data da VM.
#
# Uso: bootstrap-portainer-stack.sh <arquivo.env> [--dry-run] [--keep-env-file]
#      bootstrap-portainer-stack.sh --show-webhook
#   --dry-run        mostra o payload com os valores mascarados e nao chama a API.
#   --keep-env-file  nao apaga o arquivo .env ao terminar.
#   --show-webhook   imprime a URL do webhook da stack ja existente (nao cria nada).
#
# A criacao e SINCRONA: o Portainer clona o repositorio, baixa as imagens do GHCR e sobe os
# containers antes de responder, o que passa de 1 minuto numa VM pequena. Por isso o limite da
# chamada e longo (CREATE_TIMEOUT, 1200 s) e, se a chamada falhar ou expirar, o script confere se a
# stack foi criada mesmo assim e recupera o webhook dela.
#
# O repositorio e publico, entao a stack Git nao precisa de credencial. As imagens do GHCR tambem
# sao publicas (pull anonimo).
#
# A URL do webhook e um SEGREDO: quem a conhece redeploya a stack. Ela e impressa e guardada em
# /opt/inventarium-app/secrets/portainer-stack-webhook-url (0600).
set -euo pipefail

PORTAINER_API="${PORTAINER_API:-https://127.0.0.1:9443/api}"
PUBLIC_HOST="${PUBLIC_HOST:-portainer.inventariumifpe.me}"
REPO_URL="${REPO_URL:-https://github.com/ifpebj-ti/inventario-patrimonio-cces}"
REPO_REF="${REPO_REF:-refs/heads/main}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.production.yml}"
STACK_NAME="${STACK_NAME:-inventarium}"
CREATE_TIMEOUT="${CREATE_TIMEOUT:-1200}"
PW_FILE="/opt/inventarium-app/secrets/portainer-admin-password"
WEBHOOK_FILE="/opt/inventarium-app/secrets/portainer-stack-webhook-url"

log() { printf '[bootstrap-portainer-stack] %s\n' "$*"; }
die() { printf '[bootstrap-portainer-stack] ERRO: %s\n' "$*" >&2; exit 1; }

env_file=""
dry_run=0
keep=0
show_webhook=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) dry_run=1 ;;
    --keep-env-file) keep=1 ;;
    --show-webhook) show_webhook=1 ;;
    -*) die "opcao desconhecida: $arg" ;;
    *) env_file="$arg" ;;
  esac
done
if [ "$show_webhook" -eq 0 ]; then
  [ -n "$env_file" ] || die "uso: $0 <arquivo.env> [--dry-run] [--keep-env-file]  |  $0 --show-webhook"
  [ -r "$env_file" ] || die "arquivo nao encontrado: $env_file"
fi
[ "$(id -u)" -eq 0 ] || die "execute como root."
command -v jq >/dev/null 2>&1 || die "jq nao instalado."

# -k: certificado autoassinado do Portainer, em loopback.
curl_api() { curl -fsS --max-time 60 -k "$@"; }

portainer_login() {
  [ -s "$PW_FILE" ] || die "senha de admin do Portainer ausente em $PW_FILE."
  local login jwt
  login="$(jq -n --arg p "$(cat "$PW_FILE")" '{username: "admin", password: $p}')"
  jwt="$(curl_api -X POST "$PORTAINER_API/auth" -H 'Content-Type: application/json' --data "$login" | jq -r .jwt)"
  if [ -z "$jwt" ] || [ "$jwt" = "null" ]; then
    die "falha ao autenticar no Portainer (a senha de admin foi alterada na UI? ajuste $PW_FILE)."
  fi
  auth=(-H "Authorization: Bearer $jwt")
}

stack_id_by_name() {
  curl_api "${auth[@]}" "$PORTAINER_API/stacks" \
    | jq -r --arg n "$STACK_NAME" '.[] | select(.Name == $n) | .Id' | head -n1
}

# Imprime e guarda a URL do webhook de uma stack (JSON da stack em $1).
publish_webhook() {
  local stack_json="$1" webhook url
  webhook="$(jq -r '.AutoUpdate.Webhook // empty' <<<"$stack_json")"
  [ -n "$webhook" ] || die "a stack existe, mas sem webhook de atualizacao. Ative 'GitOps updates > Webhook' na UI do Portainer."
  url="https://${PUBLIC_HOST}/api/stacks/webhooks/${webhook}"
  (umask 077; printf '%s\n' "$url" > "$WEBHOOK_FILE")
  log "WEBHOOK (segredo! guarde como secret PORTAINER_STACK_WEBHOOK_URL do environment 'production'):"
  printf '%s\n' "$url"
}

if [ "$show_webhook" -eq 1 ]; then
  portainer_login
  existing="$(stack_id_by_name)"
  [ -n "$existing" ] || die "a stack '$STACK_NAME' nao existe."
  publish_webhook "$(curl_api "${auth[@]}" "$PORTAINER_API/stacks/${existing}")"
  exit 0
fi

# Mesmas variaveis que o compose exige (:?). IMAGE_TAG precisa ser uma tag que exista no GHCR.
required=(POSTGRES_USER POSTGRES_PASSWORD POSTGRES_DB SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD
  SECURITY_TOKEN_SECRET GOOGLE_OAUTH_CLIENT_ID IMAGE_TAG)

# Variaveis que o docker-compose.production.yml realmente usa. Qualquer outra (por exemplo um .env
# copiado do desenvolvimento, com GOOGLE_OAUTH_CLIENT_SECRET) e descartada com aviso: segredo que a
# producao nao usa nao deve ir para o servidor.
allowed=(POSTGRES_USER POSTGRES_PASSWORD POSTGRES_DB SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD
  SECURITY_TOKEN_SECRET GOOGLE_OAUTH_CLIENT_ID GOOGLE_ALLOWED_DOMAINS CORS_ALLOWED_ORIGINS IMAGE_TAG
  SYSTEM_ADMIN_USERNAME SYSTEM_ADMIN_PASSWORD FORWARD_HEADERS_STRATEGY BACKEND_PORT FRONTEND_PORT
  SPRING_JPA_HIBERNATE_DDL_AUTO AUTH_LOGIN_IP_MAX_FAILURES AUTH_LOGIN_USER_MAX_FAILURES
  AUTH_LOGIN_WINDOW AUTH_LOGIN_BLOCK_DURATION)
allowed_json="$(printf '%s\n' "${allowed[@]}" | jq -R . | jq -s .)"

# .env -> [{"name":..,"value":..}], ignorando comentarios e linhas em branco. Aceita valores com '='.
# O Portainer repassa o valor como esta (nao interpreta aspas como um .env), entao as aspas que
# envolvem o valor ("..." ou '...') e o CR de arquivos com fim de linha do Windows sao removidos.
env_all="$(jq -R -s --arg dq '"' --arg sq "'" '
  split("\n")
  | map(sub("\r$"; ""))
  | map(select(test("^[[:space:]]*[A-Za-z_][A-Za-z0-9_]*=")))
  | map(sub("^[[:space:]]+"; ""))
  | map({name: (split("=")[0]), value: (split("=")[1:] | join("="))})
  | map(.value |= (
      if length >= 2 and ((startswith($dq) and endswith($dq)) or (startswith($sq) and endswith($sq)))
      then .[1:-1] else . end))
' < "$env_file")"

dropped="$(jq -r --argjson allowed "$allowed_json" \
  'map(select(.name as $n | ($allowed | index($n)) | not) | .name) | unique | join(", ")' <<<"$env_all")"
if [ -n "$dropped" ]; then
  log "AVISO: variaveis ignoradas (nao usadas pelo compose de producao): $dropped"
fi
env_json="$(jq --argjson allowed "$allowed_json" 'map(select(.name as $n | $allowed | index($n)))' <<<"$env_all")"

for key in "${required[@]}"; do
  value="$(jq -r --arg k "$key" '.[] | select(.name == $k) | .value' <<<"$env_json")"
  [ -n "$value" ] || die "variavel obrigatoria ausente ou vazia em $env_file: $key"
done

sys_pw="$(jq -r '.[] | select(.name == "SYSTEM_ADMIN_PASSWORD") | .value' <<<"$env_json")"
if [ -n "$sys_pw" ] && [ "${#sys_pw}" -lt 12 ]; then
  die "SYSTEM_ADMIN_PASSWORD precisa ter no minimo 12 caracteres (senao o backend nao cria a conta)."
fi
[ "$(jq -r '.[] | select(.name == "SECURITY_TOKEN_SECRET") | .value | length' <<<"$env_json")" -ge 32 ] \
  || die "SECURITY_TOKEN_SECRET deve ter no minimo 32 caracteres (use: openssl rand -hex 32)."

webhook_uuid="$(cat /proc/sys/kernel/random/uuid)"
payload="$(jq -n \
  --arg name "$STACK_NAME" --arg url "$REPO_URL" --arg ref "$REPO_REF" --arg file "$COMPOSE_FILE" \
  --arg webhook "$webhook_uuid" --argjson env "$env_json" \
  '{name: $name, repositoryURL: $url, repositoryReferenceName: $ref, composeFile: $file,
    repositoryAuthentication: false,
    autoUpdate: {webhook: $webhook, forcePullImage: true, forceUpdate: false},
    env: $env}')"

if [ "$dry_run" -eq 1 ]; then
  log "dry-run: payload (valores mascarados):"
  jq '.env |= map(.value = "***")' <<<"$payload"
  exit 0
fi

tmp="$(mktemp)"
chmod 600 "$tmp"
trap 'rm -f "$tmp"' EXIT

portainer_login

endpoint_id="$(curl_api "${auth[@]}" "$PORTAINER_API/endpoints" | jq -r '.[0].Id // empty')"
[ -n "$endpoint_id" ] || die "nenhum ambiente Docker no Portainer (o container precisa iniciar com -H unix:///var/run/docker.sock)."

existing="$(stack_id_by_name)"
[ -z "$existing" ] || die "a stack '$STACK_NAME' ja existe (id $existing). Para ver o webhook: $0 --show-webhook"

log "criando a stack '$STACK_NAME' no ambiente $endpoint_id (clona o repositorio e baixa as imagens; pode levar varios minutos)"
printf '%s' "$payload" > "$tmp"
if ! response="$(curl -fsS --max-time "$CREATE_TIMEOUT" -k "${auth[@]}" -X POST \
  -H 'Content-Type: application/json' --data "@$tmp" \
  "$PORTAINER_API/stacks/create/standalone/repository?endpointId=${endpoint_id}")"; then
  # Falha ou timeout: o Portainer pode ter concluido a criacao mesmo assim. Confere antes de desistir.
  log "A chamada falhou ou expirou. Conferindo se a stack foi criada mesmo assim..."
  sleep 5
  existing="$(stack_id_by_name)"
  [ -n "$existing" ] \
    || die "a API recusou ou nao concluiu a criacao da stack. Confira os campos contra /api/docs da versao instalada e os logs: docker logs portainer"
  log "A stack existe (id $existing): recuperando os dados dela."
  response="$(curl_api "${auth[@]}" "$PORTAINER_API/stacks/${existing}")"
fi
stack_id="$(jq -r '.Id // empty' <<<"$response")"
[ -n "$stack_id" ] || die "resposta inesperada da API: $(jq -c 'del(.Env)' <<<"$response" | head -c 400)"

returned_webhook="$(jq -r '.AutoUpdate.Webhook // empty' <<<"$response")"
if [ -n "$returned_webhook" ] && [ "$returned_webhook" != "$webhook_uuid" ]; then
  log "AVISO: o webhook retornado difere do enviado; usando o que o Portainer registrou."
fi

log "stack pronta (id $stack_id)."
publish_webhook "$response"

if [ "$keep" -eq 0 ]; then
  shred -u "$env_file" 2>/dev/null || rm -f "$env_file"
  log "arquivo $env_file apagado."
fi
