#!/usr/bin/env bash
# Instala a configuracao versionada do Nginx (infra/nginx/app-vm) na VM da aplicacao.
#
# Uso: install-nginx-config.sh <diretorio-com-a-copia-de-infra/nginx/app-vm>
#
# - Faz backup do que existe, aplica os arquivos, valida com `nginx -t` e recarrega.
# - Se a validacao falhar, restaura o backup e sai com erro (o Nginx em execucao nao e tocado).
# - Os vhosts HTTPS (sites-tls/) so entram quando o certificado existe; sem ele, o Nginx fica apenas
#   com a porta 80 (desafio ACME + redirecionamento), que e o estado do primeiro boot.
# - allowlist de admin e upstreams da observabilidade sao GERADOS aqui a partir de
#   /etc/inventarium/edge.env (ADMIN_CIDRS, OBSERVABILITY_IP) e nunca vao para o Git.
set -euo pipefail

EDGE_ENV="${EDGE_ENV:-/etc/inventarium/edge.env}"
DOMAIN="${DOMAIN:-inventariumifpe.me}"
NGINX_ETC="${NGINX_ETC:-/etc/nginx}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/inventarium}"
CERT_DIR="${CERT_DIR:-/etc/letsencrypt/live/${DOMAIN}}"

log() { printf '[install-nginx-config] %s\n' "$*"; }
die() { printf '[install-nginx-config] ERRO: %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "execute como root (sudo)."
[ "$#" -eq 1 ] || die "uso: $0 <diretorio-fonte>"
src="$1"
if [ ! -f "$src/nginx.conf" ] || [ ! -d "$src/conf.d" ] || [ ! -d "$src/snippets" ] || [ ! -d "$src/sites-tls" ]; then
  die "$src nao parece ser infra/nginx/app-vm (faltam nginx.conf, conf.d, snippets ou sites-tls)."
fi
[ -r "$EDGE_ENV" ] || die "$EDGE_ENV ausente (ADMIN_CIDRS e OBSERVABILITY_IP)."

# shellcheck disable=SC1090
. "$EDGE_ENV"

# Os valores entram em arquivos de configuracao do Nginx: valide o formato para impedir injecao.
cidr_re='^[0-9]{1,3}(\.[0-9]{1,3}){3}(/[0-9]{1,2})?$'
ip_re='^[0-9]{1,3}(\.[0-9]{1,3}){3}$'
[ -n "${ADMIN_CIDRS:-}" ] || die "ADMIN_CIDRS vazio em $EDGE_ENV."
[ -n "${OBSERVABILITY_IP:-}" ] || die "OBSERVABILITY_IP vazio em $EDGE_ENV."
[[ "$OBSERVABILITY_IP" =~ $ip_re ]] || die "OBSERVABILITY_IP invalido: $OBSERVABILITY_IP"
for cidr in $ADMIN_CIDRS; do
  [[ "$cidr" =~ $cidr_re ]] || die "CIDR de admin invalido: $cidr"
done
# LB_CIDR e opcional: subnet do Load Balancer (lb_public_subnet_cidr do Terraform). Vazio mantem o modo
# direto (internet -> Nginx), que continua valido como rollback.
for cidr in ${LB_CIDR:-}; do
  [[ "$cidr" =~ $cidr_re ]] || die "CIDR do LB invalido: $cidr"
done

stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT

mkdir -p "$stage/conf.d" "$stage/snippets" "$stage/generated"
cp "$src"/conf.d/*.conf "$stage/conf.d/"
cp "$src"/snippets/*.conf "$stage/snippets/"

tls_installed=0
if [ -r "$CERT_DIR/fullchain.pem" ] && [ -r "$CERT_DIR/privkey.pem" ]; then
  cp "$src"/sites-tls/*.conf "$stage/conf.d/"
  tls_installed=1
  log "certificado encontrado em $CERT_DIR: vhosts HTTPS incluidos."
else
  log "AVISO: sem certificado em $CERT_DIR; instalando apenas a porta 80. Rode issue-certificates.sh."
fi

{
  echo "# Gerado por install-nginx-config.sh. Nao edite."
  for cidr in $ADMIN_CIDRS; do
    echo "allow ${cidr};"
  done
  echo "deny all;"
} > "$stage/generated/admin-allowlist.conf"

# Com o LB na frente, $remote_addr seria sempre o IP dele: o rate limit por IP, a allowlist do Portainer
# e o log perderiam o cliente. O Nginx so confia no X-Forwarded-For quando a conexao vem de LB_CIDR; um
# cabecalho forjado por quem chega direto na 443 e ignorado.
{
  echo "# Gerado por install-nginx-config.sh. Nao edite."
  if [ -n "${LB_CIDR:-}" ]; then
    for cidr in $LB_CIDR; do
      echo "set_real_ip_from ${cidr};"
    done
    echo "real_ip_header X-Forwarded-For;"
    echo "real_ip_recursive on;"
  fi
} > "$stage/generated/real-ip.conf"

# Health check do LB (porta 9081): so a subnet do LB. Sem LB_CIDR, ninguem.
{
  echo "# Gerado por install-nginx-config.sh. Nao edite."
  for cidr in ${LB_CIDR:-}; do
    echo "allow ${cidr};"
  done
  echo "deny all;"
} > "$stage/generated/lb-allowlist.conf"

cat > "$stage/generated/upstreams.conf" <<EOF
# Gerado por install-nginx-config.sh. Nao edite.
upstream sonar_upstream {
    server ${OBSERVABILITY_IP}:9000;
}

upstream grafana_upstream {
    server ${OBSERVABILITY_IP}:3000;
}
EOF

# Backup do estado atual (somente o que este script gerencia).
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
backup="$BACKUP_DIR/nginx-$(date -u +%Y%m%dT%H%M%SZ).tar.gz"
tar -C "$NGINX_ETC" -czf "$backup" nginx.conf conf.d inventarium 2>/dev/null || tar -C "$NGINX_ETC" -czf "$backup" nginx.conf conf.d
log "backup em $backup"

# Portas internas do LB (vhosts na 9080, health na 9081). O SELinux so deixa o Nginx escutar em portas
# rotuladas http_port_t, e o firewalld do host so abre as dos servicos http/https; sem isso o Nginx
# nao sobe nelas. Idempotente. As regras do firewalld ficam so para a subnet do LB e sao aplicadas em
# runtime e no permanente, sem --reload (o reload derrubaria as regras que o Docker cria).
internal_ports="9080 9081"

ensure_internal_ports() {
  if command -v getenforce >/dev/null 2>&1 && [ "$(getenforce)" != "Disabled" ]; then
    command -v semanage >/dev/null 2>&1 || dnf -y install policycoreutils-python-utils >/dev/null
    labeled="$(semanage port -l | awk '$1 == "http_port_t"')"
    for port in $internal_ports; do
      if ! grep -qw "$port" <<<"$labeled"; then
        log "SELinux: rotulando a porta $port como http_port_t."
        semanage port -a -t http_port_t -p tcp "$port"
      fi
    done
  fi
  if [ -n "${LB_CIDR:-}" ] && command -v firewall-cmd >/dev/null 2>&1 && systemctl is-active --quiet firewalld; then
    for cidr in $LB_CIDR; do
      for port in $internal_ports; do
        rule="rule family=ipv4 source address=${cidr} port port=${port} protocol=tcp accept"
        if ! firewall-cmd --permanent --query-rich-rule="$rule" >/dev/null 2>&1; then
          log "firewalld: liberando a porta $port para $cidr."
          firewall-cmd --permanent --add-rich-rule="$rule" >/dev/null
          firewall-cmd --add-rich-rule="$rule" >/dev/null
        fi
      done
    done
  fi
}

# Depois do reload, confere que o Nginx realmente escuta nas portas internas: o `nginx -t` nao pega
# um bind recusado pelo SELinux, e o reload falho deixa o Nginx antigo no ar sem avisar.
internal_ports_listening() {
  local attempt sockets port all
  for attempt in 1 2 3 4 5 6 7 8 9 10; do
    sockets="$(ss -ltn 2>/dev/null || true)"
    all=1
    for port in $internal_ports; do
      grep -Eq ":${port}[[:space:]]" <<<"$sockets" || all=0
    done
    [ "$all" -eq 1 ] && return 0
    sleep 1
  done
  return 1
}

if [ "$tls_installed" -eq 1 ]; then
  ensure_internal_ports
fi

apply() {
  install -D -m 0644 "$src/nginx.conf" "$NGINX_ETC/nginx.conf"
  rm -f "$NGINX_ETC"/conf.d/*.conf
  cp "$stage"/conf.d/*.conf "$NGINX_ETC/conf.d/"
  rm -rf "$NGINX_ETC/inventarium"
  mkdir -p "$NGINX_ETC/inventarium/snippets" "$NGINX_ETC/inventarium/generated"
  cp "$stage"/snippets/*.conf "$NGINX_ETC/inventarium/snippets/"
  cp "$stage"/generated/*.conf "$NGINX_ETC/inventarium/generated/"
  chmod 0644 "$NGINX_ETC"/conf.d/*.conf "$NGINX_ETC"/inventarium/snippets/*.conf
  chmod 0640 "$NGINX_ETC"/inventarium/generated/*.conf
  mkdir -p /var/www/certbot
  if command -v restorecon >/dev/null 2>&1; then
    restorecon -R "$NGINX_ETC" /var/www/certbot || true
  fi
}

rollback() {
  log "restaurando o backup $backup"
  rm -rf "$NGINX_ETC/conf.d" "$NGINX_ETC/inventarium"
  tar -C "$NGINX_ETC" -xzf "$backup"
}

apply
if ! nginx -t; then
  rollback
  die "nginx -t falhou; configuracao anterior restaurada."
fi

if systemctl is-active --quiet nginx; then
  systemctl reload nginx
  log "nginx recarregado."
  if [ "$tls_installed" -eq 1 ] && command -v ss >/dev/null 2>&1 && ! internal_ports_listening; then
    rollback
    systemctl reload nginx
    die "o Nginx nao passou a escutar em $internal_ports (SELinux ou firewall?); configuracao anterior restaurada."
  fi
else
  systemctl enable --now nginx
  log "nginx iniciado."
fi

# Mantem apenas os 10 backups de configuracao mais recentes.
# shellcheck disable=SC2012
ls -1t "$BACKUP_DIR"/nginx-*.tar.gz 2>/dev/null | tail -n +11 | xargs -r rm -f --
log "concluido."
