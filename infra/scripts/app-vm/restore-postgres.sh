#!/usr/bin/env bash
# Teste de restauracao (restore drill) de um dump gerado por backup-postgres.sh.
#
# Uso: restore-postgres.sh <arquivo.dump> [--keep]
#
# Restaura o dump em um banco TEMPORARIO (inventarium_restore_check) dentro do mesmo container do
# Postgres, lista a contagem de linhas por tabela e remove o banco. NAO toca no banco de producao.
# Com --keep o banco temporario e mantido para inspecao manual (remova depois com dropdb).
#
# Restaurar em producao e um procedimento manual (parar o backend, recriar o banco, pg_restore) descrito
# em docs/wiki/Deploy-em-Producao-OCI.md.
set -euo pipefail

CONTAINER="${POSTGRES_CONTAINER:-inventarium-postgres}"
SCRATCH_DB="inventarium_restore_check"

log() { printf '[restore-postgres] %s\n' "$*"; }
die() { printf '[restore-postgres] ERRO: %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "execute como root."
[ "$#" -ge 1 ] || die "uso: $0 <arquivo.dump> [--keep]"
dump="$1"
keep=0
if [ "${2:-}" = "--keep" ]; then
  keep=1
fi
[ -s "$dump" ] || die "arquivo inexistente ou vazio: $dump"

sum_file="${dump}.sha256"
if [ -r "$sum_file" ]; then
  (cd "$(dirname "$dump")" && sha256sum -c "$(basename "$sum_file")") || die "checksum nao confere."
else
  log "AVISO: sem ${sum_file}; integridade nao verificada."
fi

docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null | grep -qx true \
  || die "container $CONTAINER nao esta rodando."

cleanup() {
  if [ "$keep" -eq 0 ]; then
    docker exec "$CONTAINER" sh -c "dropdb -U \"\$POSTGRES_USER\" --if-exists $SCRATCH_DB" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

docker exec "$CONTAINER" sh -c "dropdb -U \"\$POSTGRES_USER\" --if-exists $SCRATCH_DB && createdb -U \"\$POSTGRES_USER\" $SCRATCH_DB"
log "restaurando $dump em $SCRATCH_DB"
docker exec -i "$CONTAINER" sh -c "pg_restore -U \"\$POSTGRES_USER\" -d $SCRATCH_DB --no-owner --exit-on-error" < "$dump"

log "contagem de linhas por tabela:"
docker exec "$CONTAINER" sh -c "psql -U \"\$POSTGRES_USER\" -d $SCRATCH_DB -At -c \"
  SELECT relname || ' = ' || n_live_tup FROM pg_stat_user_tables ORDER BY relname\""

log "restauracao validada."
if [ "$keep" -eq 1 ]; then
  log "banco $SCRATCH_DB mantido; remova com: docker exec $CONTAINER dropdb -U <usuario> $SCRATCH_DB"
fi
