#!/usr/bin/env bash
# Instala ou atualiza o coletor Grafana Alloy numa VM (Oracle Linux 8). Idempotente. Normalmente
# chamado por infra/scripts/deploy-alloy.sh.
#
# Uso (como root, na VM): bootstrap-alloy.sh <diretorio-com-infra-alloy> <app|observability> <ip-da-observabilidade>
#   <diretorio-com-infra-alloy>  copia de infra/alloy enviada do repositorio.
#   <app|observability>          rotulo "vm" das metricas e logs (filtro nos dashboards).
#   <ip-da-observabilidade>      onde estao o Prometheus (9090) e o Loki (3100); 127.0.0.1 na propria VM de
#                                observabilidade.
#
# Nao ha segredos aqui: so o IP e o nome da VM, escritos em /opt/inventarium-alloy/.env.
set -euo pipefail

APP_DIR="/opt/inventarium-alloy"
ENV_FILE="$APP_DIR/.env"

log() { printf '[bootstrap-alloy] %s\n' "$*"; }
die() { printf '[bootstrap-alloy] ERRO: %s\n' "$*" >&2; exit 1; }

src="${1:-}"
vm_name="${2:-}"
obs_host="${3:-}"
[ -n "$src" ] && [ -n "$vm_name" ] && [ -n "$obs_host" ] \
  || die "uso: $0 <diretorio-com-infra-alloy> <app|observability> <ip-da-observabilidade>"
case "$vm_name" in app | observability) ;; *) die "nome de VM invalido: $vm_name (use app ou observability)" ;; esac
case "$obs_host" in *[!0-9.]*) die "ip invalido: $obs_host" ;; esac
[ -f "$src/docker-compose.alloy.yml" ] && [ -f "$src/config.alloy" ] || die "arquivos do Alloy nao encontrados em $src"
[ "$(id -u)" -eq 0 ] || die "execute como root."
command -v docker >/dev/null 2>&1 || die "Docker nao instalado."
docker compose version >/dev/null 2>&1 || die "plugin docker compose ausente."

log "copiando o Alloy para $APP_DIR"
mkdir -p "$APP_DIR"
install -m 0644 "$src/docker-compose.alloy.yml" "$APP_DIR/docker-compose.alloy.yml"
install -m 0644 "$src/config.alloy" "$APP_DIR/config.alloy"
(
  umask 077
  printf 'OBS_HOST=%s\nVM_NAME=%s\n' "$obs_host" "$vm_name" > "$ENV_FILE"
)

cd "$APP_DIR"
log "validando o compose"
docker compose --env-file "$ENV_FILE" -f docker-compose.alloy.yml config -q

# Aviso (nao bloqueia): o Alloy tenta de novo sozinho, mas ajuda a achar erro de rede/NSG logo.
for target in "9090/-/ready" "3100/ready"; do
  if ! curl -fsS --max-time 5 -o /dev/null "http://${obs_host}:${target}"; then
    log "AVISO: http://${obs_host}:${target} nao respondeu (a stack de observabilidade esta no ar? NSG liberado?)"
  fi
done

log "subindo o Alloy (baixa a imagem na primeira vez)"
docker compose --env-file "$ENV_FILE" -f docker-compose.alloy.yml up -d --remove-orphans

log "concluido. Status:"
docker compose --env-file "$ENV_FILE" -f docker-compose.alloy.yml ps --format 'table {{.Name}}\t{{.Status}}'
log "logs do Alloy: sudo docker logs alloy --tail 30"
