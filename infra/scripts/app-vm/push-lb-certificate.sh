#!/usr/bin/env bash
# Envia o certificado Let's Encrypt da VM ao Load Balancer da OCI e troca o certificado do listener HTTPS
# (ADR 0009). O LB termina o TLS, entao precisa do certificado; o certbot continua emitindo e renovando na
# VM (HTTP-01 pela porta 80 do LB) e este script fecha o ciclo, sem tarefa manual a cada 90 dias.
#
# Chamado no fim de renew-certificates.sh (2x ao dia) e de issue-certificates.sh. E idempotente: compara o
# sha256 do fullchain.pem com o ultimo enviado, entao uma falha e tentada de novo na proxima execucao.
# Sem LB_ID no edge.env (modo direto, antes do LB existir) nao faz nada.
#
# Autenticacao: instance principal (dynamic group da VM, politica em edge_iam.tf), sem chaves de API. A
# CLI da OCI roda em container, como o certbot; a chave privada nunca passa pelo Terraform nem pelo state.
#
# edge.env: LB_ID (OCID do LB), LB_LISTENER_NAME (padrao "https").
# Ambiente opcional: OCI_CLI_IMAGE (fixe a versao ou o digest), LB_KEEP_CERTS (padrao 2).
set -euo pipefail

EDGE_ENV="${EDGE_ENV:-/etc/inventarium/edge.env}"
DOMAIN="${DOMAIN:-inventariumifpe.me}"
CERT_DIR="${CERT_DIR:-/etc/letsencrypt/live/${DOMAIN}}"
STATE_DIR="${STATE_DIR:-/var/lib/inventarium}"
STATE_FILE="$STATE_DIR/lb-cert.sha256"
# A Oracle so documenta a tag latest dessa imagem; fixe um digest em OCI_CLI_IMAGE quando puder.
OCI_CLI_IMAGE="${OCI_CLI_IMAGE:-ghcr.io/oracle/oci-cli:latest}"
HELPER="${LB_HELPER:-/opt/inventarium-app/src/scripts/lb-rotate-certificate.py}"
KEEP_CERTS="${LB_KEEP_CERTS:-2}"

log() { printf '[push-lb-certificate] %s\n' "$*"; }
die() { printf '[push-lb-certificate] ERRO: %s\n' "$*" >&2; exit 1; }

[ -r "$EDGE_ENV" ] || die "$EDGE_ENV ausente."
# shellcheck disable=SC1090
. "$EDGE_ENV"

if [ -z "${LB_ID:-}" ]; then
  log "LB_ID vazio no edge.env: modo direto, nada a enviar."
  exit 0
fi

# Os valores viram variaveis de ambiente do container e nomes na API: valide o formato.
[[ "$LB_ID" =~ ^ocid1\.loadbalancer\.[A-Za-z0-9._-]+$ ]] || die "LB_ID invalido em $EDGE_ENV."
listener="${LB_LISTENER_NAME:-https}"
[[ "$listener" =~ ^[A-Za-z0-9_-]{1,64}$ ]] || die "LB_LISTENER_NAME invalido em $EDGE_ENV."
[[ "$KEEP_CERTS" =~ ^[1-9]$ ]] || die "LB_KEEP_CERTS deve ser um digito de 1 a 9."
[ -r "$CERT_DIR/fullchain.pem" ] && [ -r "$CERT_DIR/privkey.pem" ] || die "certificado nao encontrado em $CERT_DIR."
[ -r "$HELPER" ] || die "script auxiliar ausente: $HELPER"

new_sum="$(sha256sum "$CERT_DIR/fullchain.pem" | awk '{print $1}')"
old_sum="$(cat "$STATE_FILE" 2>/dev/null || true)"
if [ "$new_sum" = "$old_sum" ]; then
  log "o certificado do LB ja esta atualizado."
  exit 0
fi

cert_name="inventarium-$(date -u +%Y%m%d)-${new_sum:0:8}"
log "enviando $cert_name ao listener $listener"

# /etc/letsencrypt inteiro (somente leitura): live/ so tem links simbolicos para archive/.
docker run --rm \
  -e OCI_CLI_AUTH=instance_principal \
  -e LB_ID="$LB_ID" \
  -e LB_LISTENER_NAME="$listener" \
  -e LB_CERT_NAME="$cert_name" \
  -e LB_KEEP_CERTS="$KEEP_CERTS" \
  -e CERT_DIR="$CERT_DIR" \
  -v /etc/letsencrypt:/etc/letsencrypt:ro \
  -v "$HELPER:/lb-rotate-certificate.py:ro" \
  --entrypoint python3 \
  "$OCI_CLI_IMAGE" /lb-rotate-certificate.py

mkdir -p "$STATE_DIR"
chmod 700 "$STATE_DIR"
printf '%s\n' "$new_sum" > "$STATE_FILE"
log "certificado do LB atualizado."
