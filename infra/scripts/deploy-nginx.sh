#!/usr/bin/env bash
# Atualiza o Nginx e os scripts operacionais da VM da aplicacao a partir do repositorio, SEM recriar a VM.
# Rode na SUA maquina (Git Bash/WSL/Linux), na raiz do repositorio apos o merge:
#
#   infra/scripts/deploy-nginx.sh <ip-ou-host> [usuario-ssh]
#
# Envia infra/nginx/app-vm e infra/scripts/app-vm, atualiza /opt/inventarium-app/bin e executa
# install-nginx-config.sh na VM, que valida (nginx -t) e faz rollback sozinho se a config falhar.
set -euo pipefail

host="${1:-}"
user="${2:-opc}"
[ -n "$host" ] || { echo "uso: $0 <ip-ou-host> [usuario-ssh]" >&2; exit 1; }

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
if [ ! -d infra/nginx/app-vm ] || [ ! -d infra/scripts/app-vm ]; then
  echo "execute dentro do repositorio." >&2
  exit 1
fi

stage="/tmp/inventarium-deploy-$$"
echo "[deploy-nginx] enviando para ${user}@${host}:${stage}"

tar -czf - -C infra nginx/app-vm scripts/app-vm \
  | ssh -o StrictHostKeyChecking=accept-new "${user}@${host}" \
      "rm -rf '${stage}' && mkdir -p '${stage}' && tar -xzf - -C '${stage}'"

# O heredoc e 'EOF' (sem expansao local): o diretorio temporario chega como $1. Expandir ${stage}
# no cliente e proposital (e um caminho gerado por este script).
# shellcheck disable=SC2029
ssh "${user}@${host}" "sudo bash -s -- '${stage}'" <<'EOF'
set -euo pipefail
stage="$1"
trap 'rm -rf "$stage"' EXIT
src="/opt/inventarium-app/src"
mkdir -p "$src"
rm -rf "$src/nginx" "$src/scripts"
cp -r "$stage/nginx/app-vm" "$src/nginx"
cp -r "$stage/scripts/app-vm" "$src/scripts"
install -m 0750 "$src"/scripts/*.sh /opt/inventarium-app/bin/
install -m 0644 "$src"/scripts/systemd/* /etc/systemd/system/
systemctl daemon-reload
/opt/inventarium-app/bin/install-nginx-config.sh "$src/nginx"
EOF

echo "[deploy-nginx] concluido."
