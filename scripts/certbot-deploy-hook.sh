#!/usr/bin/env bash
#
# Certbot deploy hook — reloads the nginx container after a certificate renews
# so it starts serving the new cert without a manual restart.
#
# Install once on the host (see NGINX_TROUBLESHOOTING.md → "TLS certificate
# renewal"):
#
#   sudo install -m 0755 scripts/certbot-deploy-hook.sh \
#       /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
#
# Every successful `certbot renew` then runs this automatically.
set -euo pipefail

CONTAINER="${NGINX_CONTAINER:-elcafe-nginx-proxy}"

if docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
    # Reload picks up the new cert with zero dropped connections.
    docker exec "$CONTAINER" nginx -s reload
    echo "certbot-deploy-hook: reloaded $CONTAINER"
else
    echo "certbot-deploy-hook: $CONTAINER not running, skipping reload"
fi
