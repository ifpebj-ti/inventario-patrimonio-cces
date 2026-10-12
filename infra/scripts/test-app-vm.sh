#!/usr/bin/env bash
# Testa os scripts de infra/scripts/app-vm num container Oracle Linux 8 (o mesmo SO da VM).
# Requer Docker. Uso, na raiz do repositorio:  bash infra/scripts/test-app-vm.sh
set -euo pipefail

# Evita a conversao de caminhos do Git Bash para Windows nos argumentos do docker.
export MSYS_NO_PATHCONV=1

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# No Windows (Git Bash) o docker.exe precisa do caminho no formato C:/...; no Linux, pwd basta.
mount_root="$(cd "$repo_root" && { pwd -W 2>/dev/null || pwd; })"

docker run --rm \
  -v "${mount_root}/infra:/src:ro" \
  oraclelinux:8 \
  bash /src/scripts/tests/ol8-app-vm-checks.sh
