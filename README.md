# 🤖 Assistant RAG Multimodal

Assistant intelligent basé sur la technologie RAG (Retrieval-Augmented Generation) permettant d'interroger vos documents de manière conversationnelle.

## 📋 Description

L'Assistant RAG Multimodal est une application qui permet aux utilisateurs d'uploader divers types de documents et de poser des questions à leur sujet. L'assistant utilise l'intelligence artificielle pour analyser les documents et fournir des réponses contextuelles précises.

## ✨ Fonctionnalités

- **📁 Upload de documents** : Prise en charge de multiples formats
  - PDF
  - DOCX
  - TXT
  - Images (JPG, PNG)
  - Taille maximale : 50MB par fichier

- **💬 Interface conversationnelle** : Posez vos questions en langage naturel

- **🔍 Fonctionnalités avancées** :
  - Résumer les documents
  - Trouver les images contenues dans les documents
  - Identifier les points clés

- **📂 Gestion des fichiers** : Visualisation et organisation des documents uploadés

## 🖥️ Interface Utilisateur

L'application dispose d'une interface intuitive composée de :

- **Panneau latéral gauche** : Liste des fichiers uploadés avec aperçu
- **Zone centrale** : Interface de conversation avec l'assistant
- **Boutons d'action rapide** :
  - Résumer les documents
  - Trouver les images
  - Points clés

## 🚀 Installation

```bash
# Cloner le repository
git clone https://github.com/yahykader/Chatbot-IA-Conversationnel-avec-RAG-Multimodal.git

# Accéder au répertoire
cd AGENT-AI-RAG

# lancer le base de donnée postgres dockorisé
docker compose up

# lance le backend
mvn install 

# excuter le jar qui est dans le target 
java -jar nom du jar.jar


# Installer les dépendances pour le front end Angular pour le dossier agentic-rag-ui
npm install

# Lancer l'application
ng serve


## 💡 Utilisation

1. **Uploader des documents** : Cliquez sur "Cliquez ou glissez un fichier" dans la zone d'upload
2. **Sélectionner vos fichiers** : Formats acceptés - PDF, DOCX, TXT, Images (max 50MB)
3. **Poser vos questions** : Utilisez la zone de texte en bas pour interroger vos documents
4. **Utiliser les raccourcis** : Utilisez les boutons pour résumer, trouver des images ou extraire les points clés

### Raccourcis clavier

- `Enter` : Envoyer un message
- `Shift + Enter` : Nouvelle ligne dans le message

## 🛠️ Technologies utilisées

- Interface utilisateur moderne et responsive
- Traitement du langage naturel (NLP)
- Intelligence artificielle pour l'analyse documentaire
- Architecture RAG (Retrieval-Augmented Generation)

## 📊 Formats de documents supportés

| Format | Extension | Taille max |
|--------|-----------|------------|
| PDF | .pdf | 50MB |
| Word | .docx | 50MB |
| Texte | .txt | 50MB |
| Images | .jpg, .png | 50MB |

## 🔒 Sécurité et confidentialité

- Les documents sont traités de manière sécurisée
- Aucune donnée n'est partagée avec des tiers
- Possibilité de supprimer les fichiers à tout moment

## 🤝 Contribution

Les contributions sont les bienvenues ! N'hésitez pas à :

1. Fork le projet
2. Créer une branche pour votre fonctionnalité (`git checkout -b feature/AmazingFeature`)
3. Commit vos changements (`git commit -m 'Add some AmazingFeature'`)
4. Push vers la branche (`git push origin feature/AmazingFeature`)
5. Ouvrir une Pull Request

## 📝 Licence

Ce projet est sous licence [TYPE_DE_LICENCE]. Voir le fichier `LICENSE` pour plus de détails.

## 📧 Contact

Pour toute question ou suggestion, n'hésitez pas à ouvrir une issue sur GitHub.

## 🎯 Roadmap

- [ ] Support de formats supplémentaires (Excel, PowerPoint)
- [ ] Export des conversations
- [ ] Mode hors ligne
- [ ] Intégration avec des services cloud
- [ ] Support multilingue avancé
- [ ] API REST pour intégration externe

## 🙏 Remerciements

Merci à tous les contributeurs qui vont participer à ce projet !

---

**Note** : Cette application nécessite une connexion internet pour fonctionner correctement.