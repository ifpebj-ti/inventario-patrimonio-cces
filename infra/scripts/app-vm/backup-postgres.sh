#!/usr/bin/env bash
# Backup logico do Postgres (pg_dump -Fc) do container inventarium-postgres.
# Chamado por inventarium-backup.timer (diario). Passos:
#   1. dump consistente para /var/backups/inventarium (0600), com checksum;
#   2. envio ao bucket do Object Storage com instance principal (sem chaves na VM);
#   3. descarte de copias locais com mais de LOCAL_RETENTION_DAYS dias.
# A retencao remota e feita pela lifecycle policy do bucket (Terraform).
#
# Variaveis (de /etc/inventarium/edge.env): BACKUP_BUCKET, OBJECT_STORAGE_NAMESPACE.
# Sem BACKUP_BUCKET o backup fica apenas local e o script sai com erro, para o systemd acusar a falha.
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/var/backups/inventarium}"
CONTAINER="${POSTGRES_CONTAINER:-inventarium-postgres}"
LOCAL_RETENTION_DAYS="${LOCAL_RETENTION_DAYS:-7}"
OCI_CLI_IMAGE="${OCI_CLI_IMAGE:-ghcr.io/oracle/oci-cli:latest}"

log() { printf '[backup-postgres] %s\n' "$*"; }
die() { printf '[backup-postgres] ERRO: %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "execute como root."
docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null | grep -qx true \
  || die "container $CONTAINER nao esta rodando."

umask 077
mkdir -p "$BACKUP_DIR"
ts="$(date -u +%Y%m%dT%H%M%SZ)"
name="inventarium-${ts}.dump"
partial="${BACKUP_DIR}/${name}.partial"

# As credenciais vem do ambiente do proprio container; nada de senha em argumento de processo.
docker exec "$CONTAINER" sh -c 'pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"' > "$partial"
[ -s "$partial" ] || { rm -f "$partial"; die "dump vazio."; }
mv "$partial" "${BACKUP_DIR}/${name}"
(cd "$BACKUP_DIR" && sha256sum "$name" > "${name}.sha256")
log "dump gerado: ${name} ($(du -h "${BACKUP_DIR}/${name}" | cut -f1))"

status=0
if [ -n "${BACKUP_BUCKET:-}" ] && [ -n "${OBJECT_STORAGE_NAMESPACE:-}" ]; then
  for file in "$name" "${name}.sha256"; do
    # --user 0: o container le arquivos 0600 de root; o diretorio e montado somente leitura.
    if ! docker run --rm --user 0 -e OCI_CLI_AUTH=instance_principal \
      -v "${BACKUP_DIR}:/backups:ro" "$OCI_CLI_IMAGE" \
      os object put --namespace-name "$OBJECT_STORAGE_NAMESPACE" --bucket-name "$BACKUP_BUCKET" \
      --file "/backups/${file}" --name "postgres/${file}" --force >/dev/null; then
      log "FALHA ao enviar ${file} ao bucket ${BACKUP_BUCKET}."
      status=1
    fi
  done
  if [ "$status" -eq 0 ]; then
    log "enviado ao bucket ${BACKUP_BUCKET}."
  fi
else
  log "BACKUP_BUCKET/OBJECT_STORAGE_NAMESPACE nao definidos: backup apenas local."
  status=1
fi

find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'inventarium-*.dump' -o -name 'inventarium-*.dump.sha256' \) \
  -mtime +"$LOCAL_RETENTION_DAYS" -delete

exit "$status"
