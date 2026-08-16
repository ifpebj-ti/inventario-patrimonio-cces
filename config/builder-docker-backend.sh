#!/bin/bash

DIR="$(dirname "${BASH_SOURCE[0]}")"
DIR="$(cd "$DIR" >/dev/null 2>&1 && pwd)"
BUILD_CONTEXT="$DIR/../backend"

# Nome da imagem e versão
IMAGE_NAME="inventarium-back"
IMAGE_TAG="0.0.1"
BUILD_ARG="build/libs/*.jar"
OUTPUT_PATH="/home/lucascs/Documentos"

docker build --build-arg JAR_FILE="$BUILD_ARG" -t $IMAGE_NAME:$IMAGE_TAG "$BUILD_CONTEXT" && \
docker save -o $OUTPUT_PATH/${IMAGE_NAME//\//-}-$IMAGE_TAG.tar $IMAGE_NAME:$IMAGE_TAG
