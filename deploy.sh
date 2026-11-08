#!/usr/bin/env sh
# Builds the image locally and ships it to the server over SSH.
# Usage: DEPLOY_HOST=user@server ./deploy.sh   (DEPLOY_DIR defaults to ~/finance)
set -eu

: "${DEPLOY_HOST:?Set DEPLOY_HOST, e.g. DEPLOY_HOST=root@1.2.3.4}"
DEPLOY_DIR="${DEPLOY_DIR:-finance}"

./gradlew test buildFatJar -q
docker build --platform linux/amd64 -t finance-bot:latest .

ssh "$DEPLOY_HOST" "mkdir -p $DEPLOY_DIR/data"
scp docker-compose.yml "$DEPLOY_HOST:$DEPLOY_DIR/"
docker save finance-bot:latest | gzip | ssh "$DEPLOY_HOST" "gunzip | docker load"
ssh "$DEPLOY_HOST" "cd $DEPLOY_DIR && test -f .env || { echo 'Create $DEPLOY_DIR/.env on the server (see .env.example)'; exit 1; } && docker compose up -d && docker compose ps"
