#!/usr/bin/env bash
# Bootstrap da VM da aplicacao (Oracle Linux 8). Executado UMA vez pelo cloud-init no primeiro boot
# e seguro de reexecutar: tudo aqui e idempotente. Log em /var/log/inventarium-bootstrap.log.
#
# Entradas (nao secretas): /etc/inventarium/edge.env, escrito pelo cloud-init a partir do Terraform.
# Arquivos do repositorio ficam em /opt/inventarium-app/src (nginx/ e scripts/).
set -euo pipefail

SRC="${SRC:-/opt/inventarium-app/src}"
APP_DIR="/opt/inventarium-app"
EDGE_ENV="/etc/inventarium/edge.env"
PORTAINER_VERSION="${PORTAINER_VERSION:-2.39.5}"
MARKER="/var/lib/inventarium/bootstrap.done"

log() { printf '[bootstrap-vm] %s %s\n' "$(date -u +%H:%M:%S)" "$*"; }

[ "$(id -u)" -eq 0 ] || { echo "execute como root" >&2; exit 1; }
mkdir -p /var/lib/inventarium

# Uma VM em subnet publica so tem internet depois que o IP publico reservado e associado, o que pode
# acontecer segundos apos o primeiro boot. Espera ate ~15 min antes de desistir.
log "aguardando conectividade com a internet..."
for _ in $(seq 1 90); do
  if curl -fsS --max-time 5 -o /dev/null https://download.docker.com/linux/centos/docker-ce.repo; then
    break
  fi
  sleep 10
done
curl -fsS --max-time 5 -o /dev/null https://download.docker.com/linux/centos/docker-ce.repo \
  || { log "sem internet; abortando."; exit 1; }

# shellcheck disable=SC1090
. "$EDGE_ENV"

log "instalando pacotes base"
dnf -y install dnf-utils jq curl tar >/dev/null
# O nginx do AppStream padrao do OL8 e antigo; tenta uma stream mais nova e segue com a padrao se nao existir.
dnf -y module enable nginx:1.24 >/dev/null 2>&1 || true
dnf -y install nginx >/dev/null

# Firewall ANTES do Docker. O firewall-cmd conversa com o firewalld por D-Bus e, logo depois de
# instalar pacotes e subir o Docker numa VM pequena, o daemon nao responde a tempo ("NoReply") e o
# Docker tambem mexe no firewalld ao iniciar. firewall-offline-cmd edita a configuracao direto nos
# arquivos, sem D-Bus, entao paramos o daemon, editamos e subimos de novo (idempotente).
log "firewall"
if command -v firewall-offline-cmd >/dev/null 2>&1; then
  systemctl stop firewalld 2>/dev/null || true
  firewall-offline-cmd --add-service=http --add-service=https
  systemctl enable --now firewalld
fi

# O Nginx precisa abrir conexoes para 127.0.0.1 (front, back, Portainer) e para a VM de observabilidade.
log "SELinux"
setsebool -P httpd_can_network_connect 1

log "instalando o Docker"
if ! command -v docker >/dev/null 2>&1; then
  dnf config-manager --add-repo=https://download.docker.com/linux/centos/docker-ce.repo
  dnf -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin >/dev/null
fi
systemctl enable --now docker

mkdir -p "$APP_DIR/bin" "$APP_DIR/secrets" /var/www/certbot /etc/letsencrypt /var/backups/inventarium
chmod 700 "$APP_DIR/secrets" /var/backups/inventarium

log "instalando scripts operacionais"
install -m 0750 "$SRC"/scripts/*.sh "$APP_DIR/bin/"
install -m 0644 "$SRC"/scripts/systemd/* /etc/systemd/system/
systemctl daemon-reload

log "configurando o Nginx (apenas a porta 80 ate existir certificado)"
"$APP_DIR/bin/install-nginx-config.sh" "$SRC/nginx"

log "subindo o Portainer ${PORTAINER_VERSION} (somente em 127.0.0.1:9443)"
pw_file="$APP_DIR/secrets/portainer-admin-password"
if [ ! -s "$pw_file" ]; then
  (umask 077; openssl rand -base64 24 | tr -d '/+=\n' | cut -c1-24 > "$pw_file")
fi
docker volume create portainer_data >/dev/null
if ! docker inspect portainer >/dev/null 2>&1; then
  docker run -d --name portainer --restart=always \
    -p 127.0.0.1:9443:9443 \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v portainer_data:/data \
    -v "$pw_file":/run/secrets/portainer_admin_password:ro \
    "portainer/portainer-ce:${PORTAINER_VERSION}" \
    -H unix:///var/run/docker.sock \
    --admin-password-file /run/secrets/portainer_admin_password
fi

log "habilitando backup diario e renovacao de certificados"
systemctl enable --now inventarium-backup.timer inventarium-certbot-renew.timer

touch "$MARKER"
log "bootstrap concluido. Proximo passo: configurar o DNS e rodar $APP_DIR/bin/issue-certificates.sh"
