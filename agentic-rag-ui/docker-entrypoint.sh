#!/bin/sh
# =============================================================================
# docker-entrypoint.sh
# Injecte les variables d'environnement dans nginx.conf au démarrage
# Dev  : BACKEND_URL=http://backend:8090
# Prod : BACKEND_URL=https://rag-backend-prod-xxx.run.app
# =============================================================================

# Valeur par défaut pour le dev
BACKEND_URL=${BACKEND_URL:-http://backend:8090}

echo "🔧 BACKEND_URL = $BACKEND_URL"

# Remplace ${BACKEND_URL} dans le template et génère nginx.conf
envsubst '${BACKEND_URL}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf

echo "✅ nginx.conf généré"

# Démarre Nginx en foreground
nginx -g 'daemon off;'