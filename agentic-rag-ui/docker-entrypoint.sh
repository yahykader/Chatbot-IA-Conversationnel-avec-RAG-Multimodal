#!/bin/sh
# =============================================================================
# docker-entrypoint.sh
# Injecte les variables d'environnement dans nginx.conf au démarrage
# Dev  : BACKEND_URL=http://backend:8090
# Prod : BACKEND_URL=https://rag-backend-prod-xxx.run.app
# =============================================================================

# Valeur par défaut pour le dev
#!/bin/sh
BACKEND_URL=${BACKEND_URL:-http://backend:8090}

echo "🔧 BACKEND_URL = $BACKEND_URL"

sed "s|BACKEND_URL_PLACEHOLDER|${BACKEND_URL}|g" \
  /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf

echo "✅ nginx.conf généré"
nginx -g 'daemon off;'