#!/usr/bin/env sh
# Builds the jar and the Mini App locally and deploys them: the server only builds a thin image around the jar.
# Usage: DEPLOY_HOST=vpn ./deploy.sh   (DEPLOY_DIR defaults to /opt/finance)
set -eu

: "${DEPLOY_HOST:?Set DEPLOY_HOST, e.g. DEPLOY_HOST=vpn}"
DEPLOY_DIR="${DEPLOY_DIR:-/opt/finance}"

./gradlew test buildFatJar -q
(cd webapp && npm ci --silent && npm run build --silent)

ssh "$DEPLOY_HOST" "mkdir -p $DEPLOY_DIR/build/libs $DEPLOY_DIR/data $DEPLOY_DIR/webapp"
scp -q Dockerfile docker-compose.yml Caddyfile "$DEPLOY_HOST:$DEPLOY_DIR/"
scp -q build/libs/finance.jar "$DEPLOY_HOST:$DEPLOY_DIR/build/libs/"
# upload the Mini App next to the live one, then copy it in place;
# the directory itself stays (Caddy bind-mounts it, renaming would detach the mount)
ssh "$DEPLOY_HOST" "rm -rf $DEPLOY_DIR/webapp.new && mkdir -p $DEPLOY_DIR/webapp.new"
scp -q -r webapp/dist/. "$DEPLOY_HOST:$DEPLOY_DIR/webapp.new/"
ssh "$DEPLOY_HOST" "cd $DEPLOY_DIR && find webapp -mindepth 1 -delete && cp -a webapp.new/. webapp/ && rm -rf webapp.new"
ssh "$DEPLOY_HOST" "cd $DEPLOY_DIR && test -f .env || { echo 'Create $DEPLOY_DIR/.env on the server (see .env.example)'; exit 1; } \
  && docker compose up -d --build && docker image prune -f --filter label=finance.app=bot && docker compose ps"
