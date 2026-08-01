#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

if [[ ! -f "$DEPLOY_DIR/.env" ]]; then
  echo "Missing $DEPLOY_DIR/.env. Copy .env.example and configure secrets first." >&2
  exit 1
fi

if ! systemctl is-active --quiet docker; then
  systemctl start docker
fi

cd "$DEPLOY_DIR"
docker compose --env-file .env up -d
docker compose ps
