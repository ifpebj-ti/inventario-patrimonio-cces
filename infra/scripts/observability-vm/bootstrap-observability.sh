#!/usr/bin/env bash
# Instala e atualiza a stack de observabilidade na VM privada (Oracle Linux 8). Idempotente: pode
# rodar de novo a cada atualizacao da stack. Normalmente chamado por infra/scripts/deploy-observability.sh.
#
# Uso (como root, na VM): bootstrap-observability.sh <diretorio-com-a-stack>
#   <diretorio-com-a-stack> e a copia de infra/observability enviada do repositorio.
#
# Segredos (senha do Grafana e do Postgres do SonarQube) sao gerados na PRIMEIRA execucao em
# /opt/inventarium-observability/.env (0600). Nunca vao para o Git, para o Terraform nem para logs.
set -euo pipefail

APP_DIR="/opt/inventarium-observability"
ENV_FILE="$APP_DIR/.env"
SYSCTL_FILE="/etc/sysctl.d/99-inventarium-sonarqube.conf"

log() { printf '[bootstrap-observability] %s\n' "$*"; }
die() { printf '[bootstrap-observability] ERRO: %s\n' "$*" >&2; exit 1; }

src="${1:-}"
[ -n "$src" ] || die "uso: $0 <diretorio-com-a-stack>"
[ -f "$src/docker-compose.observability.yml" ] || die "docker-compose.observability.yml nao encontrado em $src"
[ "$(id -u)" -eq 0 ] || die "execute como root."
command -v docker >/dev/null 2>&1 || die "Docker nao instalado (o cloud-init da VM o instala no primeiro boot)."
docker compose version >/dev/null 2>&1 || die "plugin docker compose ausente."

# O Elasticsearch embutido do SonarQube nao sobe sem estes limites do kernel.
log "ajustando o kernel para o SonarQube"
cat > "$SYSCTL_FILE" <<'SYSCTL'
vm.max_map_count = 262144
fs.file-max = 131072
SYSCTL
sysctl -q -p "$SYSCTL_FILE"

log "copiando a stack para $APP_DIR"
mkdir -p "$APP_DIR"
for item in docker-compose.observability.yml prometheus loki tempo grafana; do
  rm -rf "${APP_DIR:?}/$item"
  cp -r "$src/$item" "$APP_DIR/$item"
done

if [ ! -s "$ENV_FILE" ]; then
  log "gerando segredos em $ENV_FILE (0600)"
  (
    umask 077
    {
      printf 'GRAFANA_ADMIN_PASSWORD=%s\n' "$(openssl rand -hex 16)"
      printf 'SONAR_DB_PASSWORD=%s\n' "$(openssl rand -hex 16)"
    } > "$ENV_FILE"
  )
else
  chmod 600 "$ENV_FILE"
  log "segredos ja existem; mantidos."
fi

cd "$APP_DIR"
log "validando o compose"
docker compose --env-file "$ENV_FILE" -f docker-compose.observability.yml config -q

log "subindo a stack (baixa as imagens na primeira vez)"
docker compose --env-file "$ENV_FILE" -f docker-compose.observability.yml up -d --remove-orphans

log "concluido. Containers:"
docker compose --env-file "$ENV_FILE" -f docker-compose.observability.yml ps --format 'table {{.Name}}\t{{.Status}}'
log "senha do Grafana: sudo grep GRAFANA_ADMIN_PASSWORD $ENV_FILE"
