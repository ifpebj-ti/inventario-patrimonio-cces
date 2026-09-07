#!/bin/bash

DIR="$(dirname "${BASH_SOURCE[0]}")"
DIR="$(cd "$DIR" >/dev/null 2>&1 && pwd)"
BUILD_CONTEXT="$DIR/../frontend"
ENV_FILE="$DIR/../.env"

# Nome da imagem e versão
IMAGE_NAME="inventarium-front"
IMAGE_TAG="0.0.1"

# O Next inlina NEXT_PUBLIC_* no bundle durante o build, então o client id do
# Google precisa vir como build arg — não adianta defini-lo só no ambiente do
# container. Lido do .env da raiz para não duplicar o valor em dois lugares.
if [ -f "$ENV_FILE" ]; then
  GOOGLE_OAUTH_CLIENT_ID="$(grep -m1 '^GOOGLE_OAUTH_CLIENT_ID=' "$ENV_FILE" | cut -d= -f2-)"
fi

if [ -z "$GOOGLE_OAUTH_CLIENT_ID" ]; then
  echo "ERRO: GOOGLE_OAUTH_CLIENT_ID não definido em $ENV_FILE" >&2
  exit 1
fi

docker build \
  --build-arg NEXT_PUBLIC_GOOGLE_CLIENT_ID="$GOOGLE_OAUTH_CLIENT_ID" \
  -t $IMAGE_NAME:$IMAGE_TAG "$BUILD_CONTEXT"
