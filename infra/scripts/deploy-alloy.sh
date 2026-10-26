#!/usr/bin/env bash
# Instala ou atualiza o coletor Grafana Alloy numa das VMs, SEM recriar a VM.
# Rode na SUA maquina (Git Bash/WSL/Linux), na raiz do repositorio:
#
#   infra/scripts/deploy-alloy.sh <app|observability> <ip-da-vm-da-aplicacao> [ip-da-vm-de-observabilidade] [usuario-ssh]
#
# app:           conecta direto na VM da aplicacao; o Alloy envia para o IP privado da observabilidade.
# observability: a VM nao tem IP publico, entao o SSH passa pela VM da aplicacao (ProxyJump); o
#                Alloy envia para 127.0.0.1 (o Prometheus e o Loki rodam na propria VM).
# O IP privado padrao da observabilidade e o definido no Terraform (10.42.0.74).
set -euo pipefail

target="${1:-}"
app_host="${2:-}"
obs_host="${3:-10.42.0.74}"
user="${4:-opc}"
usage="uso: $0 <app|observability> <ip-da-vm-da-aplicacao> [ip-da-vm-de-observabilidade] [usuario-ssh]"
[ -n "$target" ] && [ -n "$app_host" ] || { echo "$usage" >&2; exit 1; }

case "$target" in
  app)
    ssh_opts=(-o StrictHostKeyChecking=accept-new)
    ssh_host="$app_host"
    alloy_obs_host="$obs_host"
    ;;
  observability)
    ssh_opts=(-o StrictHostKeyChecking=accept-new -J "${user}@${app_host}")
    ssh_host="$obs_host"
    alloy_obs_host="127.0.0.1"
    ;;
  *)
    echo "$usage" >&2
    exit 1
    ;;
esac

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
[ -f infra/alloy/docker-compose.alloy.yml ] || { echo "execute dentro do repositorio." >&2; exit 1; }

stage="/tmp/inventarium-alloy-$$"
echo "[deploy-alloy] enviando para ${user}@${ssh_host}:${stage} (vm=${target}, destino das metricas=${alloy_obs_host})"

# Expandir ${stage} no cliente e proposital (e um caminho gerado por este script).
# shellcheck disable=SC2029
tar -czf - -C infra alloy scripts/alloy-vm \
  | ssh "${ssh_opts[@]}" "${user}@${ssh_host}" \
      "rm -rf '${stage}' && mkdir -p '${stage}' && tar -xzf - -C '${stage}'"

# O heredoc e 'REMOTE' (sem expansao local): o diretorio, a VM e o destino chegam como $1, $2 e $3.
# shellcheck disable=SC2029
ssh "${ssh_opts[@]}" "${user}@${ssh_host}" "sudo bash -s -- '${stage}' '${target}' '${alloy_obs_host}'" <<'REMOTE'
set -euo pipefail
stage="$1"
vm="$2"
obs="$3"
trap 'rm -rf "$stage"' EXIT
install -d -m 0750 /opt/inventarium-alloy/bin
install -m 0750 "$stage"/scripts/alloy-vm/*.sh /opt/inventarium-alloy/bin/
/opt/inventarium-alloy/bin/bootstrap-alloy.sh "$stage/alloy" "$vm" "$obs"
REMOTE

echo "[deploy-alloy] concluido."
