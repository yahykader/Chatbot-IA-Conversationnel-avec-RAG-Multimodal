#!/usr/bin/env bash

# Détection de l'OS
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    OS="Windows"
    HOME_DIR="$USERPROFILE"
elif [[ "$OSTYPE" == "darwin"* ]]; then
    OS="macOS"
    HOME_DIR="$HOME"
else
    OS="Linux"
    HOME_DIR="$HOME"
fi

echo "🚀 Configuration RAG Application"
echo ""
echo "OS détecté : $OS"
echo "Utilisateur : $(whoami)"
echo ""

# Créer les dossiers
UPLOADS_DIR="$HOME_DIR/Downloads/uploads"
LOGS_DIR="$HOME_DIR/Downloads/logs"

echo "📁 Création des dossiers..."
mkdir -p "$UPLOADS_DIR/extracted-images"
mkdir -p "$LOGS_DIR"
echo "✅ $UPLOADS_DIR"
echo "✅ $LOGS_DIR"
echo ""

# Créer .env
if [ ! -f .env ]; then
    echo "📝 Création de .env..."
    cat > .env << EOF
# OpenAI API Key
OPENAI_API_KEY=sk-votre-clef-ici
EOF
    echo "✅ Fichier .env créé"
fi

echo ""
echo "✅ Configuration terminée !"
echo ""
echo "Lancez : docker-compose up -d"