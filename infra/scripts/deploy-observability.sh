#!/usr/bin/env bash
# Instala ou atualiza a stack de observabilidade na VM privada, SEM recriar a VM.
# Rode na SUA maquina (Git Bash/WSL/Linux), na raiz do repositorio:
#
#   infra/scripts/deploy-observability.sh <ip-da-vm-da-aplicacao> [ip-da-vm-de-observabilidade] [usuario-ssh]
#
# A VM de observabilidade nao tem IP publico: o SSH passa pela VM da aplicacao (ProxyJump), que a
# alcanca pela rede privada (o NSG libera a porta 22 vinda da subnet da aplicacao).
# O padrao do IP privado e o definido no Terraform (10.42.0.74).
set -euo pipefail

app_host="${1:-}"
obs_host="${2:-10.42.0.74}"
user="${3:-opc}"
[ -n "$app_host" ] || { echo "uso: $0 <ip-da-vm-da-aplicacao> [ip-da-vm-de-observabilidade] [usuario-ssh]" >&2; exit 1; }

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
[ -f infra/observability/docker-compose.observability.yml ] || { echo "execute dentro do repositorio." >&2; exit 1; }

ssh_opts=(-o StrictHostKeyChecking=accept-new -J "${user}@${app_host}")
stage="/tmp/inventarium-observability-$$"
echo "[deploy-observability] enviando para ${user}@${obs_host} (via ${app_host}):${stage}"

# Expandir ${stage} no cliente e proposital (e um caminho gerado por este script).
# shellcheck disable=SC2029
tar -czf - -C infra observability scripts/observability-vm \
  | ssh "${ssh_opts[@]}" "${user}@${obs_host}" \
      "rm -rf '${stage}' && mkdir -p '${stage}' && tar -xzf - -C '${stage}'"

# O heredoc e 'REMOTE' (sem expansao local): o diretorio temporario chega como $1.
# shellcheck disable=SC2029
ssh "${ssh_opts[@]}" "${user}@${obs_host}" "sudo bash -s -- '${stage}'" <<'REMOTE'
set -euo pipefail
stage="$1"
trap 'rm -rf "$stage"' EXIT
install -d -m 0750 /opt/inventarium-observability/bin
install -m 0750 "$stage"/scripts/observability-vm/*.sh /opt/inventarium-observability/bin/
/opt/inventarium-observability/bin/bootstrap-observability.sh "$stage/observability"
REMOTE

echo "[deploy-observability] concluido."
