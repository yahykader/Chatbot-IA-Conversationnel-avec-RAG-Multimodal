# =========================
# Stage 1 : Build Angular
# =========================
FROM node:25-alpine AS build
WORKDIR /app

# Copier seulement les fichiers de dépendances pour tirer parti du cache Docker
COPY package*.json ./

#  Installer les dépendances rapidement et de façon déterministe
RUN npm ci --legacy-peer-deps --loglevel verbose

# Copier le reste du projet
COPY . .

# Vérifier que les fichiers d'environnement existent
RUN ls -R src/environments

# Build Angular pour production
RUN npm run build -- --configuration=production --output-path=dist

# =========================
# Stage 2 : Serveur Nginx
# =========================
FROM nginx:alpine

# Copier la configuration Nginx
COPY nginx.conf /etc/nginx/nginx.conf

# Copier le build Angular depuis l'étape précédente
COPY --from=build /app/dist/browser /usr/share/nginx/html

# Exposer le port
EXPOSE 80

# Lancer Nginx en mode foreground
CMD ["nginx", "-g", "daemon off;"]
