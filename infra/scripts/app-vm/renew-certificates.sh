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

# Com o Load Balancer na frente, ele guarda o certificado que o navegador ve: envia o renovado. Roda a
# cada execucao (e idempotente), entao uma falha anterior e tentada de novo em 12 h; sem LB_ID e no-op.
/opt/inventarium-app/bin/push-lb-certificate.sh
