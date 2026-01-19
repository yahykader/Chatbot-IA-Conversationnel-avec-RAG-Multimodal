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

## Back-End

**Flux**
## 📊 Exemple de résultat

## Flux complet d'un fichier DOCX avec images
```
📄 Git-lab CI-CD.docx
    ↓
┌─────────────────────────────────────────────────────┐
│  1. DÉTECTION DU TYPE                                │
│     → Fichier Office avec images                    │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│  2. EXTRACTION DU TEXTE                              │
│     → "GitLab CI/CD est un outil..."                │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│  3. DÉCOUPAGE EN SEGMENTS (chunks)                   │
│     → Segment 1: "GitLab CI/CD est..."              │
│     → Segment 2: "Les pipelines permettent..."      │
│     → Segment 3: "Configuration du fichier..."      │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│  4. EMBEDDING DES SEGMENTS (OpenAI)                  │
│     Segment 1 → [0.234, -0.521, 0.892, ...]  (1536) │
│     Segment 2 → [0.123, -0.456, 0.789, ...]  (1536) │
│     Segment 3 → [-0.321, 0.654, 0.987, ...]  (1536) │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│  5. STOCKAGE PGVECTOR (text_embeddings)              │
│     INSERT INTO text_embeddings                      │
│     (embedding_id, embedding, text, metadata)        │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│  6. EXTRACTION DES IMAGES                            │
│     → Image 1: Diagramme pipeline                   │
│     → Image 2: Screenshot configuration             │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│  7. ANALYSE VISION AI (GPT-4 Vision)                 │
│     Image 1 → "Diagramme montrant un pipeline..."   │
│     Image 2 → "Capture d'écran d'un fichier..."     │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│  8. EMBEDDING DES DESCRIPTIONS (OpenAI)              │
│     Description 1 → [0.456, -0.789, 0.123, ...] (1536)│
│     Description 2 → [-0.234, 0.567, 0.890, ...] (1536)│
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│  9. STOCKAGE PGVECTOR (image_embeddings)             │
│     INSERT INTO image_embeddings                     │
│     (embedding_id, embedding, text, metadata)        │
└─────────────────────────────────────────────────────┘
    ↓
✅ TERMINÉ : Fichier indexé en tant que vecteurs dans PgVector



## 📊 Exemple de résultat pour un document Word

### Upload de `Guide_Utilisateur.docx` avec 5 images :
```
📥 Ingestion du fichier: Guide_Utilisateur.docx (3.2 MB)
🔍 Type détecté: OFFICE_WITH_IMAGES
📘🖼️ Traitement Word avec images: Guide_Utilisateur.docx

🖼️ Extraction image Word: Guide_Utilisateur_para2_img1 (800x600)
💾 Image sauvegardée: /mnt/user-data/extracted-images/Guide_Utilisateur_para2_img1.png
🤖 Vision AI: Description générée (380 caractères)
✅ Image Word 1 extraite et sauvegardée: /mnt/.../Guide_Utilisateur_para2_img1.png

🖼️ Extraction image Word: Guide_Utilisateur_para5_img1 (1024x768)
💾 Image sauvegardée: /mnt/user-data/extracted-images/Guide_Utilisateur_para5_img1.png
🤖 Vision AI: Description générée (425 caractères)
✅ Image Word 2 extraite et sauvegardée: /mnt/.../Guide_Utilisateur_para5_img1.png

🖼️ Extraction image Word: Guide_Utilisateur_para8_img1 (640x480)
💾 Image sauvegardée: /mnt/user-data/extracted-images/Guide_Utilisateur_para8_img1.png
🤖 Vision AI: Description générée (310 caractères)
✅ Image Word 3 extraite et sauvegardée: /mnt/.../Guide_Utilisateur_para8_img1.png

🖼️ Extraction image Word (header/1/para1): Guide_Utilisateur_header1_img1 (200x100)
💾 Image sauvegardée: /mnt/user-data/extracted-images/Guide_Utilisateur_header1_img1.png
✅ Image 4 (header) extraite et sauvegardée: /mnt/.../Guide_Utilisateur_header1_img1.png

🖼️ Extraction image Word (footer/1/para1): Guide_Utilisateur_footer1_img1 (150x50)
💾 Image sauvegardée: /mnt/user-data/extracted-images/Guide_Utilisateur_footer1_img1.png
✅ Image 5 (footer) extraite et sauvegardée: /mnt/.../Guide_Utilisateur_footer1_img1.png

✓ Texte indexé (8500 caractères)
✅ Document Word traité: 15 paragraphes, 8500 caractères de texte, 5 images extraites et sauvegardées
✅ Fichier ingéré avec succès: Guide_Utilisateur.docx
```

## 📁 Structure du dossier créé
```
/mnt/user-data/extracted-images/
├── Guide_Utilisateur_para2_img1.png          # Image du paragraphe 2
├── Guide_Utilisateur_para5_img1.png          # Image du paragraphe 5
├── Guide_Utilisateur_para8_img1.png          # Image du paragraphe 8
├── Guide_Utilisateur_header1_img1.png        # Logo du header
├── Guide_Utilisateur_footer1_img1.png        # Logo du footer
├── rapport_2024_page1_img1.png               # (d'un PDF)
└── architecture.png                           # (uploadée directement)


## Résultat final dans PgVector

Après l'upload de votre fichier, vous aurez :
```
text_embeddings table:
├─ 20 lignes (segments de texte)
└─ Chaque ligne contient:
   ├─ embedding_id (UUID)
   ├─ embedding (vector de 1536 dimensions)
   ├─ text (le segment de texte)
   └─ metadata (source, type, page, etc.)

image_embeddings table:
├─ 3 lignes (descriptions d'images)
└─ Chaque ligne contient:
   ├─ embedding_id (UUID)
   ├─ embedding (vector de 1536 dimensions)
   ├─ text (description de l'image)
   └─ metadata (imageName, width, height, etc.)




## 📊 Exemple de résultat pour un PDF de 3 pages
```
📥 Ingestion du fichier: rapport_2024.pdf (2.5 MB)
🔍 Type détecté: PDF_WITH_IMAGES
📕🖼️ Traitement PDF avec images: rapport_2024.pdf
📄 PDF contient 3 pages

Page 1:
  ✓ Page 1 - Texte indexé (2500 caractères)
  🖼️ Extraction image intégrée: rapport_2024_page1_img1 (800x600)
  ✅ Image intégrée 1 extraite et sauvegardée: /mnt/user-data/extracted-images/rapport_2024_page1_img1.png
  🖼️ Extraction image intégrée: rapport_2024_page1_img2 (1024x768)
  ✅ Image intégrée 2 extraite et sauvegardée: /mnt/user-data/extracted-images/rapport_2024_page1_img2.png
  🖼️ Rendu complet page: rapport_2024_page1_render (2480x3508)
  ✅ Page 1 - Rendu complet sauvegardé et indexé: /mnt/user-data/extracted-images/rapport_2024_page1_render.png

Page 2:
  ✓ Page 2 - Texte indexé (3200 caractères)
  🖼️ Extraction image intégrée: rapport_2024_page2_img1 (640x480)
  ✅ Image intégrée 3 extraite et sauvegardée: /mnt/user-data/extracted-images/rapport_2024_page2_img1.png
  🖼️ Rendu complet page: rapport_2024_page2_render (2480x3508)
  ✅ Page 2 - Rendu complet sauvegardé et indexé: /mnt/user-data/extracted-images/rapport_2024_page2_render.png

Page 3:
  ✓ Page 3 - Texte indexé (1800 caractères)
  🖼️ Rendu complet page: rapport_2024_page3_render (2480x3508)
  ✅ Page 3 - Rendu complet sauvegardé et indexé: /mnt/user-data/extracted-images/rapport_2024_page3_render.png

✅ PDF multimodal traité: 3 pages, 3 images intégrées extraites, 3 rendus de pages créés
✅ Fichier ingéré avec succès: rapport_2024.pdf
```

## 📁 Structure du dossier créé
```
/mnt/user-data/extracted-images/
├── rapport_2024_page1_img1.png          # Image intégrée (logo)
├── rapport_2024_page1_img2.png          # Image intégrée (graphique)
├── rapport_2024_page1_render.png        # Rendu complet de la page 1
├── rapport_2024_page2_img1.png          # Image intégrée (photo)
├── rapport_2024_page2_render.png        # Rendu complet de la page 2
└── rapport_2024_page3_render.png        # Rendu complet de la page 3



### Upload d'une image `architecture.jpg` :
```
📥 Ingestion du fichier: architecture.jpg (856 KB)
🔍 Type détecté: IMAGE
🖼️ Traitement image: architecture.jpg
📐 Dimensions: 1920x1080
💾 Image sauvegardée: /mnt/user-data/extracted-images/architecture.png
🤖 Vision AI: Description générée (450 caractères)
✅ Image indexée: architecture (1920x1080) - Description: 450 caractères
✅ Image standalone traitée et indexée: architecture.jpg
✅ Fichier ingéré avec succès: architecture.jpg
```

## 📁 Structure du dossier
```
/mnt/user-data/extracted-images/
├── architecture.png                       # Image uploadée directement
├── photo_equipe.png                       # Image uploadée directement
├── logo_entreprise.png                    # Image uploadée directement
├── rapport_2024_page1_img1.png           # Image extraite d'un PDF
├── rapport_2024_page1_render.png         # Rendu de page PDF
└── document_Word_image_1.png             # Image extraite d'un Word



## Front-End
📊 Votre Architecture Actuelle
┌─────────────────────────────────────────────────┐
│  ACTIONS (assistant.actions.ts)                 │
│  - Streaming: updateMessageContent, start/stop  │
│  - Messages: add, remove, clear                 │
│  - Files: upload, success, failure              │
└─────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────┐
│  REDUCER (assistant.reducer.ts)                 │
│  - EntityAdapter pour messages & files          │
│  - Compteur de séquence global                  │
│  - Gestion streaming + localStorage             │
└─────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────┐
│  EFFECTS (assistant.effects.ts)                 │
│  - sendMessageStream$ avec exhaustMap           │
│  - Gestion SSE streaming                        │
│  - Upload fichiers                              │
│  - Persistence localStorage                     │
└─────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────┐
│  SELECTORS (assistant.selectors.ts)             │
│  - selectMessagesSorted (tri par séquence)      │
│  - selectIsStreaming                            │
│  - selectCanSendMessage                         │
│  - Stats messages et fichiers                   │
└─────────────────────────────────────────────────┘

## Front-End
Pour intéger Web RTC, ils existent ttois options:

🎯 Option 1 : Solution Simple (Web Speech API - Navigateur, SANS Backend)
✅ Avantages

✅ Gratuit
✅ Pas de backend nécessaire
✅ Rapide à implémenter
✅ Fonctionne offline pour TTS

⚠️ Limitations

⚠️ Support navigateur limité (Chrome/Edge principalement)
⚠️ Qualité variable selon le navigateur
⚠️ Pas de personnalisation avancée

┌────────────────────────────────────────┐
│  Utilisateur parle dans le micro      │
└────────────────────────────────────────┘
                ↓
┌────────────────────────────────────────┐
│  Navigateur Chrome/Edge                │
│  └── Web Speech API transcrit l'audio │
└────────────────────────────────────────┘
                ↓ TEXTE
┌────────────────────────────────────────┐
│  Frontend Angular                      │
│  └── Reçoit "Bonjour comment vas-tu" │
└────────────────────────────────────────┘
                ↓ TEXTE via /chat/stream
┌────────────────────────────────────────┐
│  Backend Spring Boot (INCHANGÉ)       │
│  └── Traite comme un message normal   │
└────────────────────────────────────────┘

🎯 Option 2 : Google Speech-to-Text (AVEC Backend nécessite un google-credentials.json)

┌─────────────────────────────┐
│  Frontend Angular           │
│  └── Capture audio (blob)   │
└─────────────────────────────┘
         ↓ envoie AUDIO
┌─────────────────────────────┐
│  Backend Spring Boot        │
│  ├── /api/voice/transcribe  │ ← NOUVEAU endpoint
│  └── GoogleSpeechService    │ ← NOUVEAU service
└─────────────────────────────┘
         ↓ envoie AUDIO
┌─────────────────────────────┐
│  Google Cloud API           │
│  └── Speech-to-Text         │
└─────────────────────────────┘

🎯 Option 3 : OpenAI Whisper (AVEC Backend)

✅ Avantages de Whisper
  ✅ Qualité exceptionnelle (état de l'art)
  ✅ 99+ langues supportées
  ✅ Tous navigateurs (Firefox, Safari, etc.)
  ✅ Ponctuation automatique
  ✅ Robuste au bruit
  ✅ Détection automatique de la langue

