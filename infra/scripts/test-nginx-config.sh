#!/usr/bin/env bash
# Valida (nginx -t) a configuracao versionada em infra/nginx/app-vm usando o mesmo layout da VM:
#   /etc/nginx/nginx.conf
#   /etc/nginx/conf.d/                      <- conf.d/* e sites-tls/*
#   /etc/nginx/inventarium/snippets/        <- snippets/*
#   /etc/nginx/inventarium/generated/       <- generated.example/*
# O certificado e autoassinado e so existe dentro do container descartavel.
# Requer Docker. Usa docker cp (sem bind mounts) para funcionar igual no Linux e no Windows.
set -euo pipefail

# Evita a conversao de caminhos do Git Bash para Windows nos argumentos do docker.
export MSYS_NO_PATHCONV=1

NGINX_IMAGE="${NGINX_IMAGE:-nginx:1.26-alpine}"
OPENSSL_IMAGE="${OPENSSL_IMAGE:-alpine/openssl:latest}"
DOMAIN="inventariumifpe.me"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
src="infra/nginx/app-vm"

# Diretorio temporario dentro do repo e com caminho relativo: o docker.exe do Windows nao entende o
# /tmp do Git Bash. O trap remove tudo ao sair.
work=".nginx-test.$$"
mkdir -p "$work"
cid=""
cleanup() {
  [ -n "$cid" ] && docker rm -f "$cid" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT

# Certificado autoassinado para o caminho que o tls.conf espera.
ossl="$(docker create "$OPENSSL_IMAGE" req -x509 -newkey rsa:2048 -nodes -days 1 \
  -subj "/CN=${DOMAIN}" -keyout /tmp/privkey.pem -out /tmp/fullchain.pem)"
docker start -a "$ossl" >/dev/null
docker cp "$ossl:/tmp/privkey.pem" "$work/privkey.pem"
docker cp "$ossl:/tmp/fullchain.pem" "$work/fullchain.pem"
docker rm -f "$ossl" >/dev/null
cp "$work/fullchain.pem" "$work/chain.pem"

# Monta o layout da VM.
mkdir -p "$work/conf.d" "$work/snippets" "$work/generated"
cp "$src"/conf.d/*.conf "$work/conf.d/"
cp "$src"/sites-tls/*.conf "$work/conf.d/"
cp "$src"/snippets/*.conf "$work/snippets/"
cp "$src"/generated.example/*.conf "$work/generated/"

# Container descartavel com o layout da VM. Entrypoint sh para posicionar os arquivos antes do -t.
cid="$(docker create --entrypoint sh "$NGINX_IMAGE" -c '
  set -e
  rm -f /etc/nginx/conf.d/default.conf
  mkdir -p /etc/nginx/conf.d /etc/nginx/inventarium/snippets /etc/nginx/inventarium/generated \
           /etc/letsencrypt/live/'"$DOMAIN"' /var/www/certbot
  cp /tmp/inv-stage/conf.d/*.conf /etc/nginx/conf.d/
  cp /tmp/inv-stage/snippets/*.conf /etc/nginx/inventarium/snippets/
  cp /tmp/inv-stage/generated/*.conf /etc/nginx/inventarium/generated/
  cp /tmp/inv-stage/fullchain.pem /tmp/inv-stage/privkey.pem /tmp/inv-stage/chain.pem \
     /etc/letsencrypt/live/'"$DOMAIN"'/
  nginx -t
')"
docker cp "$src/nginx.conf" "$cid:/etc/nginx/nginx.conf"
docker cp "$work/." "$cid:/tmp/inv-stage"

if docker start -a "$cid"; then
  echo "nginx -t: OK"
else
  echo "nginx -t: FALHOU" >&2
  exit 1
fi
