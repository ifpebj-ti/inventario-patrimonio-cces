#!/usr/bin/env bash
# Emite o certificado Let's Encrypt (HTTP-01, webroot) para todos os nomes do Inventarium e liga os
# vhosts HTTPS do Nginx. O certbot roda em container (o pacote do OL8 tem conflito de Python).
#
# Uso: issue-certificates.sh [--staging] [--skip-dns-check]
#   --staging         usa a CA de teste do Let's Encrypt (nao conta no limite de emissoes; o
#                     certificado NAO e confiavel pelos navegadores). Use para ensaiar.
#   --skip-dns-check  nao confere se os nomes ja apontam para esta VM (nao recomendado).
#
# Pre-requisitos: bootstrap concluido, registros DNS A criados, portas 80/443 abertas para a internet.
set -euo pipefail

EDGE_ENV="${EDGE_ENV:-/etc/inventarium/edge.env}"
DOMAIN="${DOMAIN:-inventariumifpe.me}"
CERTBOT_IMAGE="${CERTBOT_IMAGE:-certbot/certbot:v4.2.0}"
STAGING_MARKER="/etc/letsencrypt/.inventarium-staging"
NAMES=("$DOMAIN" "www.$DOMAIN" "api.$DOMAIN" "portainer.$DOMAIN" "sonar.$DOMAIN" "grafana.$DOMAIN")

log() { printf '[issue-certificates] %s\n' "$*"; }
die() { printf '[issue-certificates] ERRO: %s\n' "$*" >&2; exit 1; }

staging=0
dns_check=1
for arg in "$@"; do
  case "$arg" in
    --staging) staging=1 ;;
    --skip-dns-check) dns_check=0 ;;
    *) die "argumento desconhecido: $arg" ;;
  esac
done

[ "$(id -u)" -eq 0 ] || die "execute como root (sudo)."
[ -r "$EDGE_ENV" ] || die "$EDGE_ENV ausente."
# shellcheck disable=SC1090
. "$EDGE_ENV"
[[ "${ACME_EMAIL:-}" =~ ^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$ ]] || die "ACME_EMAIL invalido em $EDGE_ENV."

if [ "$dns_check" -eq 1 ]; then
  my_ip="$(curl -fsS --max-time 10 https://checkip.amazonaws.com | tr -d '[:space:]')"
  [ -n "$my_ip" ] || die "nao consegui descobrir o IP publico desta VM."
  bad=0
  for name in "${NAMES[@]}"; do
    resolved="$(getent ahostsv4 "$name" | awk 'NR==1{print $1}')"
    if [ "$resolved" != "$my_ip" ]; then
      log "DNS: $name resolve para '${resolved:-nada}', esperado $my_ip"
      bad=1
    fi
  done
  [ "$bad" -eq 0 ] || die "corrija o DNS (e aguarde a propagacao) antes de emitir, para nao gastar o limite do Let's Encrypt."
fi

# Trocar de staging para producao exige --force-renewal, porque o certbot considera o certificado valido.
extra=()
if [ "$staging" -eq 1 ]; then
  extra+=(--staging)
  touch "$STAGING_MARKER"
elif [ -e "$STAGING_MARKER" ]; then
  extra+=(--force-renewal)
fi

domains=()
for name in "${NAMES[@]}"; do domains+=(-d "$name"); done

log "emitindo certificado para: ${NAMES[*]}"
docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /var/www/certbot:/var/www/certbot \
  "$CERTBOT_IMAGE" certonly --webroot -w /var/www/certbot \
  --cert-name "$DOMAIN" "${domains[@]}" \
  --email "$ACME_EMAIL" --agree-tos --no-eff-email --non-interactive "${extra[@]}"

[ "$staging" -eq 1 ] || rm -f "$STAGING_MARKER"
if command -v restorecon >/dev/null 2>&1; then
  restorecon -R /etc/letsencrypt || true
fi

log "ligando os vhosts HTTPS no Nginx"
/opt/inventarium-app/bin/install-nginx-config.sh /opt/inventarium-app/src/nginx

if [ "$staging" -eq 1 ]; then
  log "CERTIFICADO DE TESTE instalado (nao confiavel). Para o definitivo, rode sem --staging."
else
  log "certificado definitivo instalado. Renovacao automatica: systemctl list-timers inventarium-certbot-renew.timer"
fi
