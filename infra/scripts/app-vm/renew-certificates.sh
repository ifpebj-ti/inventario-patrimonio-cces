#!/usr/bin/env bash
# Renovacao dos certificados (chamado por inventarium-certbot-renew.timer, 2x ao dia). O certbot so
# renova quando faltam menos de 30 dias; depois valida e recarrega o Nginx.
set -euo pipefail

CERTBOT_IMAGE="${CERTBOT_IMAGE:-certbot/certbot:v4.2.0}"

docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /var/www/certbot:/var/www/certbot \
  "$CERTBOT_IMAGE" renew --webroot -w /var/www/certbot --non-interactive

if command -v restorecon >/dev/null 2>&1; then
  restorecon -R /etc/letsencrypt || true
fi
nginx -t
systemctl reload nginx
