#!/usr/bin/env sh
# Builds the jar locally and deploys it: the server only builds a thin image around the jar.
# Usage: DEPLOY_HOST=vpn ./deploy.sh   (DEPLOY_DIR defaults to /opt/finance)
set -eu

: "${DEPLOY_HOST:?Set DEPLOY_HOST, e.g. DEPLOY_HOST=vpn}"
DEPLOY_DIR="${DEPLOY_DIR:-/opt/finance}"

./gradlew test buildFatJar -q

ssh "$DEPLOY_HOST" "mkdir -p $DEPLOY_DIR/build/libs $DEPLOY_DIR/data"
scp -q Dockerfile docker-compose.yml "$DEPLOY_HOST:$DEPLOY_DIR/"
scp -q build/libs/finance.jar "$DEPLOY_HOST:$DEPLOY_DIR/build/libs/"
ssh "$DEPLOY_HOST" "cd $DEPLOY_DIR && test -f .env || { echo 'Create $DEPLOY_DIR/.env on the server (see .env.example)'; exit 1; } \
  && docker compose up -d --build && docker image prune -f --filter label=finance.app=bot && docker compose ps"
