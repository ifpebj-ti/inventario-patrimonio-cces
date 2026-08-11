#!/bin/bash
DIR="$(dirname "${BASH_SOURCE[0]}")"
DIR="$(cd "$DIR" >/dev/null 2>&1 && pwd)"

echo "[$(date +%c)] Limpando TODOS os arquivos do diretório: $DIR/data/..."
sudo rm -rf "$DIR"/data/*