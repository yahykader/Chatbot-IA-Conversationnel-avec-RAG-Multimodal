// ============================================================================
// SERVICE - MultimodalIngestionService.java (SANS NETTOYAGE)
// ============================================================================
package com.exemple.transactionservice.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.io.File;

@Slf4j
@Service
public class MultimodalIngestionService {

    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingStore<TextSegment> imageStore;
    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel visionModel;

    // Parsers
    private final ApachePdfBoxDocumentParser pdfParser;
    private final ApachePoiDocumentParser poiParser;
    private final ApacheTikaDocumentParser tikaParser;

    // Configuration
    private static final int MAX_IMAGE_SIZE = 5_000_000; // 5MB
    private static final Set<String> KNOWN_TEXT_TYPES = Set.of(
            "txt", "md", "csv", "json", "xml", "html", "log", "java", "py", "js", "ts", "sql"
    );
    private static final Set<String> KNOWN_PDF_TYPES = Set.of("pdf");
    private static final Set<String> KNOWN_OFFICE_TYPES = Set.of(
            "docx", "doc", "pptx", "ppt", "xlsx", "xls"
    );
    private static final Set<String> KNOWN_IMAGE_TYPES = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "tiff", "svg"
    );

    public MultimodalIngestionService(
            @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            EmbeddingModel embeddingModel,
            ChatLanguageModel visionModel) {
        this.textStore = textStore;
        this.imageStore = imageStore;
        this.embeddingModel = embeddingModel;
        this.visionModel = visionModel;

        this.pdfParser = new ApachePdfBoxDocumentParser();
        this.poiParser = new ApachePoiDocumentParser();
        this.tikaParser = new ApacheTikaDocumentParser();

        log.info("✅ MultimodalIngestionService initialisé");
        log.info("   - Formats texte: {}", KNOWN_TEXT_TYPES);
        log.info("   - Formats PDF: {}", KNOWN_PDF_TYPES);
        log.info("   - Formats Office: {}", KNOWN_OFFICE_TYPES);
        log.info("   - Formats image: {}", KNOWN_IMAGE_TYPES);
    }

    // ========================================================================
    // MÉTHODE PRINCIPALE D'INGESTION
    // ========================================================================

    public void ingestFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String extension = getFileExtension(filename).toLowerCase();

        log.info("📥 Ingestion du fichier: {} ({} KB)",
                filename, String.format("%.2f", file.getSize() / 1024.0));

        try {
            FileType fileType = detectFileType(file, extension);
            log.info("🔍 Type détecté: {}", fileType);

            switch (fileType) {
                case PDF_WITH_IMAGES -> ingestPdfWithImages(file);
                case PDF_TEXT_ONLY -> ingestPdfTextOnly(file);
                case OFFICE_WITH_IMAGES -> ingestWordWithImages(file);
                case OFFICE_TEXT_ONLY -> ingestOfficeTextOnly(file);
                case IMAGE -> ingestImageFile(file);
                case TEXT -> ingestTextFile(file);
                case UNKNOWN -> ingestWithTika(file);
            }

            log.info("✅ Fichier ingéré avec succès: {}", filename);

        } catch (Exception e) {
            log.error("❌ Erreur lors de l'ingestion du fichier: {}", filename, e);
            throw new RuntimeException("Échec de l'ingestion: " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // DÉTECTION DU TYPE DE FICHIER
    // ========================================================================

    private enum FileType {
        PDF_WITH_IMAGES, PDF_TEXT_ONLY, OFFICE_WITH_IMAGES, OFFICE_TEXT_ONLY, IMAGE, TEXT, UNKNOWN
    }

    private FileType detectFileType(MultipartFile file, String extension) throws IOException {
        if (KNOWN_IMAGE_TYPES.contains(extension)) return FileType.IMAGE;
        if (KNOWN_TEXT_TYPES.contains(extension)) return FileType.TEXT;
        if (KNOWN_PDF_TYPES.contains(extension)) {
            return pdfHasImages(file) ? FileType.PDF_WITH_IMAGES : FileType.PDF_TEXT_ONLY;
        }
        if (KNOWN_OFFICE_TYPES.contains(extension)) {
            return officeHasImages(file, extension) ? FileType.OFFICE_WITH_IMAGES : FileType.OFFICE_TEXT_ONLY;
        }
        return FileType.UNKNOWN;
    }

    private boolean pdfHasImages(MultipartFile file) {
        try {
            byte[] pdfBytes = file.getBytes();
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                int pagesToCheck = Math.min(3, document.getNumberOfPages());
                for (int i = 0; i < pagesToCheck; i++) {
                    var xObjectNames = document.getPage(i).getResources().getXObjectNames();
                    if (xObjectNames.iterator().hasNext()) {
                        log.debug("✓ PDF contient des images (page {})", i + 1);
                        return true;
                    }
                }
                return false;
            }
        } catch (Exception e) {
            log.warn("⚠️ Impossible de vérifier les images du PDF: {}", e.getMessage());
            return false;
        }
    }

    private boolean officeHasImages(MultipartFile file, String extension) {
        if ("docx".equals(extension)) {
            try (InputStream is = file.getInputStream();
                 XWPFDocument document = new XWPFDocument(is)) {
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    for (XWPFRun run : paragraph.getRuns()) {
                        if (!run.getEmbeddedPictures().isEmpty()) {
                            log.debug("✓ Document Word contient des images");
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ Impossible de vérifier les images: {}", e.getMessage());
            }
        }
        return false;
    }

    // ========================================================================
    // TRAITEMENT PDF AVEC IMAGES
    // ========================================================================

    private void ingestPdfWithImages(MultipartFile file) throws IOException {
        log.info("📕🖼️ Traitement PDF avec images: {}", file.getOriginalFilename());

        byte[] pdfBytes = file.getBytes();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);

            int totalPages = document.getNumberOfPages();
            log.info("📄 PDF contient {} pages", totalPages);

            int totalImagesExtracted = 0;
            int totalPagesRendered = 0;

            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                int pageNum = pageIndex + 1;

                // Extraction du texte (SANS NETTOYAGE)
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(document);

                if (pageText != null && !pageText.trim().isEmpty() && pageText.length() > 10) {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("page", pageNum);
                    meta.put("totalPages", totalPages);
                    meta.put("source", file.getOriginalFilename());
                    meta.put("type", "pdf_page_" + pageNum);

                    Metadata metadata = Metadata.from(sanitizeMetadata(meta));
                    indexTextWithMetadata(pageText, metadata);
                    log.debug("✓ Page {} - Texte indexé", pageNum);
                }

                // Extraction des images intégrées
                try {
                    PDPage page = document.getPage(pageIndex);
                    PDResources resources = page.getResources();

                    int imageIndexOnPage = 0;
                    for (COSName name : resources.getXObjectNames()) {
                        PDXObject xObject = resources.getXObject(name);

                        if (xObject instanceof PDImageXObject) {
                            PDImageXObject imageXObject = (PDImageXObject) xObject;
                            
                            try {
                                BufferedImage bufferedImage = imageXObject.getImage();
                                
                                if (bufferedImage != null) {
                                    totalImagesExtracted++;
                                    imageIndexOnPage++;
                                    
                                    String baseFilename = file.getOriginalFilename()
                                        .replaceAll("\\.pdf$", "")
                                        .replaceAll("[^a-zA-Z0-9_-]", "_");
                                    
                                    String imageName = String.format("%s_page%d_img%d",
                                        baseFilename, pageNum, imageIndexOnPage);
                                    
                                    String savedImagePath = saveImageToDisk(bufferedImage, imageName);
                                    
                                    Map<String, Object> metadata = new HashMap<>();
                                    metadata.put("page", pageNum);
                                    metadata.put("totalPages", totalPages);
                                    metadata.put("source", "pdf_embedded");
                                    metadata.put("filename", file.getOriginalFilename());
                                    metadata.put("imageNumber", totalImagesExtracted);
                                    metadata.put("savedPath", savedImagePath);
                                    
                                    analyzeAndIndexImage(bufferedImage, imageName, metadata);
                                    
                                    log.info("✅ Image {} extraite et sauvegardée", totalImagesExtracted);
                                }
                            } catch (Exception e) {
                                log.warn("⚠️ Erreur extraction image: {}", e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Erreur extraction images page {}: {}", pageNum, e.getMessage());
                }

                // Rendu de la page complète
                try {
                    BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, 150);
                    
                    String baseFilename = file.getOriginalFilename()
                        .replaceAll("\\.pdf$", "")
                        .replaceAll("[^a-zA-Z0-9_-]", "_");
                    
                    String pageImageName = String.format("%s_page%d_render", baseFilename, pageNum);
                    String savedPageRenderPath = saveImageToDisk(pageImage, pageImageName);
                    
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("page", pageNum);
                    metadata.put("totalPages", totalPages);
                    metadata.put("source", "pdf_rendered");
                    metadata.put("filename", file.getOriginalFilename());
                    metadata.put("savedPath", savedPageRenderPath);
                    
                    analyzeAndIndexImage(pageImage, pageImageName, metadata);
                    
                    totalPagesRendered++;
                    log.info("✅ Page {} - Rendu sauvegardé", pageNum);
                    
                } catch (Exception e) {
                    log.warn("⚠️ Erreur rendu page {}: {}", pageNum, e.getMessage());
                }
            }

            log.info("✅ PDF traité: {} pages, {} images, {} rendus", 
                totalPages, totalImagesExtracted, totalPagesRendered);
        }
    }

    // ========================================================================
    // TRAITEMENT PDF TEXTE UNIQUEMENT
    // ========================================================================

    private void ingestPdfTextOnly(MultipartFile file) throws IOException {
        log.info("📕 Traitement PDF texte: {}", file.getOriginalFilename());

        Document document;
        try (InputStream inputStream = file.getInputStream()) {
            document = pdfParser.parse(inputStream);
        }

        if (document.text() == null || document.text().isBlank()) {
            throw new IllegalArgumentException("PDF ne contient pas de texte extractible");
        }

        log.debug("📝 Texte extrait: {} caractères", document.text().length());
        
        indexDocument(document, file.getOriginalFilename(), "pdf", 1000, 100);
    }

    // ========================================================================
    // TRAITEMENT WORD AVEC IMAGES
    // ========================================================================

    private void ingestWordWithImages(MultipartFile file) throws IOException {
        log.info("📘🖼️ Traitement Word avec images: {}", file.getOriginalFilename());
        
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is)) {

            StringBuilder fullText = new StringBuilder();
            int totalImagesExtracted = 0;
            
            String baseFilename = file.getOriginalFilename()
                .replaceAll("\\.docx?$", "")
                .replaceAll("[^a-zA-Z0-9_-]", "_");

            int paragraphIndex = 0;
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                paragraphIndex++;
                
                // Extraction du texte (SANS NETTOYAGE)
                String paragraphText = paragraph.getText();
                if (paragraphText != null && !paragraphText.trim().isEmpty()) {
                    fullText.append(paragraphText).append("\n");
                }

                // Extraction des images
                int imageIndexInParagraph = 0;
                for (XWPFRun run : paragraph.getRuns()) {
                    List<XWPFPicture> pictures = run.getEmbeddedPictures();
                    
                    for (XWPFPicture picture : pictures) {
                        totalImagesExtracted++;
                        imageIndexInParagraph++;
                        
                        try {
                            byte[] imageBytes = picture.getPictureData().getData();
                            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));

                            if (image != null) {
                                String imageName = String.format("%s_para%d_img%d",
                                    baseFilename, paragraphIndex, imageIndexInParagraph);
                                
                                String savedImagePath = saveImageToDisk(image, imageName);
                                
                                Map<String, Object> metadata = new HashMap<>();
                                metadata.put("paragraphIndex", paragraphIndex);
                                metadata.put("imageNumber", totalImagesExtracted);
                                metadata.put("source", "docx");
                                metadata.put("filename", file.getOriginalFilename());
                                metadata.put("savedPath", savedImagePath);
                                
                                analyzeAndIndexImage(image, imageName, metadata);
                                
                                log.info("✅ Image {} extraite", totalImagesExtracted);
                            }
                        } catch (Exception e) {
                            log.warn("⚠️ Erreur image: {}", e.getMessage());
                        }
                    }
                }
            }

            // Headers/Footers
            try {
                for (XWPFHeader header : document.getHeaderList()) {
                    totalImagesExtracted = extractImagesFromHeaderFooter(
                        header.getParagraphs(), "header", baseFilename, 
                        file.getOriginalFilename(), totalImagesExtracted
                    );
                }
                
                for (XWPFFooter footer : document.getFooterList()) {
                    totalImagesExtracted = extractImagesFromHeaderFooter(
                        footer.getParagraphs(), "footer", baseFilename, 
                        file.getOriginalFilename(), totalImagesExtracted
                    );
                }
            } catch (Exception e) {
                log.warn("⚠️ Erreur headers/footers: {}", e.getMessage());
            }

            // Indexer le texte
            if (fullText.length() > 0) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("source", file.getOriginalFilename());
                meta.put("type", "docx");
                meta.put("imagesCount", totalImagesExtracted);

                Metadata metadata = Metadata.from(sanitizeMetadata(meta));
                indexTextWithMetadata(fullText.toString(), metadata);
            }

            log.info("✅ Word traité: {} paragraphes, {} caractères, {} images",
                paragraphIndex, fullText.length(), totalImagesExtracted);
        }
    }

    private int extractImagesFromHeaderFooter(List<XWPFParagraph> paragraphs, String location,
            String baseFilename, String originalFilename, int currentImageCount) {
        
        int imageCount = currentImageCount;
        int paragraphIndex = 0;
        
        for (XWPFParagraph paragraph : paragraphs) {
            paragraphIndex++;
            int imageIndexInParagraph = 0;
            
            for (XWPFRun run : paragraph.getRuns()) {
                List<XWPFPicture> pictures = run.getEmbeddedPictures();
                
                for (XWPFPicture picture : pictures) {
                    imageCount++;
                    imageIndexInParagraph++;
                    
                    try {
                        byte[] imageBytes = picture.getPictureData().getData();
                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));

                        if (image != null) {
                            String imageName = String.format("%s_%s%d_img%d",
                                baseFilename, location, paragraphIndex, imageIndexInParagraph);
                            
                            String savedImagePath = saveImageToDisk(image, imageName);
                            
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("location", location);
                            metadata.put("imageNumber", imageCount);
                            metadata.put("source", "docx_" + location);
                            metadata.put("filename", originalFilename);
                            metadata.put("savedPath", savedImagePath);
                            
                            analyzeAndIndexImage(image, imageName, metadata);
                        }
                    } catch (Exception e) {
                        log.warn("⚠️ Erreur image {}: {}", location, e.getMessage());
                    }
                }
            }
        }
        
        return imageCount;
    }

    // ========================================================================
    // TRAITEMENT OFFICE TEXTE UNIQUEMENT
    // ========================================================================

    private void ingestOfficeTextOnly(MultipartFile file) throws IOException {
        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase();
        log.info("📘 Traitement Office ({}): {}", extension, file.getOriginalFilename());

        Document document;
        try (InputStream inputStream = file.getInputStream()) {
            document = poiParser.parse(inputStream);
        }

        if (document.text() == null || document.text().isBlank()) {
            throw new IllegalArgumentException("Document Office vide");
        }

        log.debug("📝 Texte extrait: {} caractères", document.text().length());
        
        indexDocument(document, file.getOriginalFilename(), "office_" + extension, 1000, 100);
    }

    // ========================================================================
    // TRAITEMENT TEXTE
    // ========================================================================

    private void ingestTextFile(MultipartFile file) throws IOException {
        log.info("📄 Traitement fichier texte: {}", file.getOriginalFilename());

        String text;
        try (InputStream inputStream = file.getInputStream()) {
            text = new String(inputStream.readAllBytes());
        }

        if (text.isBlank()) {
            throw new IllegalArgumentException("Fichier texte vide");
        }

        log.debug("📝 Texte extrait: {} caractères", text.length());

        Map<String, Object> meta = new HashMap<>();
        meta.put("source", file.getOriginalFilename());
        meta.put("type", "text");

        Metadata metadata = Metadata.from(sanitizeMetadata(meta));
        indexTextWithMetadata(text, metadata);
    }

    // ========================================================================
    // TRAITEMENT TIKA
    // ========================================================================

    private void ingestWithTika(MultipartFile file) throws IOException {
        log.info("🔧 Traitement avec Tika: {}", file.getOriginalFilename());

        Document document;
        try (InputStream inputStream = file.getInputStream()) {
            document = tikaParser.parse(inputStream);
        }

        if (document.text() == null || document.text().isBlank()) {
            throw new IllegalArgumentException("Impossible d'extraire du texte");
        }

        log.debug("📝 Texte extrait: {} caractères", document.text().length());
        
        indexDocument(document, file.getOriginalFilename(), "tika_auto", 1000, 100);
    }

    // ========================================================================
    // TRAITEMENT IMAGE
    // ========================================================================

    private void ingestImageFile(MultipartFile file) throws IOException {
        log.info("🖼️ Traitement image: {}", file.getOriginalFilename());

        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("Image trop volumineuse");
        }

        BufferedImage image;
        try (InputStream inputStream = file.getInputStream()) {
            image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IllegalArgumentException("Fichier image invalide");
            }
        }

        String imageName = file.getOriginalFilename()
            .replaceAll("\\.[^.]+$", "")
            .replaceAll("[^a-zA-Z0-9_-]", "_");
        
        String savedImagePath = saveImageToDisk(image, imageName);
        
        analyzeAndIndexImage(image, imageName, Map.of(
            "standalone", 1,
            "originalFilename", file.getOriginalFilename(),
            "savedPath", savedImagePath,
            "width", image.getWidth(),
            "height", image.getHeight()
        ));
        
        log.info("✅ Image standalone traitée");
    }

    // ========================================================================
    // SAUVEGARDE IMAGE
    // ========================================================================

    private String saveImageToDisk(BufferedImage image, String imageName) throws IOException {
        String baseDir = "D:/Formation-DATA-2024/extracted-images";
        
        File directory = new File(baseDir);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        
        String filename = imageName + ".png";
        File outputFile = new File(directory, filename);
        
        ImageIO.write(image, "png", outputFile);
        
        return outputFile.getAbsolutePath();
    }

    // ========================================================================
    // INDEXATION
    // ========================================================================

    private void indexDocument(Document document, String filename, String type,
                               int chunkSize, int chunkOverlap) {

        List<TextSegment> segments = DocumentSplitters
                .recursive(chunkSize, chunkOverlap)
                .split(document);

        log.info("📊 Document divisé en {} segments", segments.size());

        int indexed = 0;
        for (TextSegment segment : segments) {
            if (segment.text() == null || segment.text().isBlank() || segment.text().length() < 10) {
                continue;
            }

            try {
                Map<String, Object> metadata = new HashMap<>();
                if (segment.metadata() != null) {
                    metadata.putAll(segment.metadata().toMap());
                }
                metadata.put("source", filename);
                metadata.put("type", type);
                metadata.put("uploadDate", System.currentTimeMillis());

                TextSegment enrichedSegment = TextSegment.from(
                        segment.text(),
                        Metadata.from(sanitizeMetadata(metadata))
                );

                Embedding embedding = embeddingModel.embed(enrichedSegment.text()).content();
                textStore.add(embedding, enrichedSegment);
                indexed++;

            } catch (Exception e) {
                log.warn("⚠️ Échec indexation segment: {}", e.getMessage());
            }
        }

        log.info("✅ {} segments indexés", indexed);
    }

    private void indexTextWithMetadata(String text, Metadata baseMetadata) {
        Document document = Document.from(text, baseMetadata);

        List<TextSegment> segments = DocumentSplitters
                .recursive(1000, 100)
                .split(document);

        log.debug("📊 Texte divisé en {} segments", segments.size());

        int indexed = 0;
        for (TextSegment segment : segments) {
            if (segment.text() == null || segment.text().isBlank() || segment.text().length() < 10) {
                continue;
            }

            try {
                Map<String, Object> metadata = new HashMap<>(segment.metadata().toMap());
                metadata.put("uploadDate", System.currentTimeMillis());

                TextSegment enrichedSegment = TextSegment.from(
                        segment.text(),
                        Metadata.from(sanitizeMetadata(metadata))
                );

                Embedding embedding = embeddingModel.embed(enrichedSegment.text()).content();
                textStore.add(embedding, enrichedSegment);
                indexed++;

            } catch (Exception e) {
                log.warn("⚠️ Échec indexation segment: {}", e.getMessage());
            }
        }

        log.debug("✅ {} segments indexés", indexed);
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> raw) {
        Map<String, Object> cleaned = new HashMap<>();
        if (raw == null) {
            return cleaned;
        }

        raw.forEach((k, v) -> {
            if (k == null || v == null) return;

            if (v instanceof Boolean b) {
                cleaned.put(k, b ? 1 : 0);
                return;
            }

            if (v instanceof String || v instanceof UUID || v instanceof Integer ||
                v instanceof Long || v instanceof Float || v instanceof Double) {
                cleaned.put(k, v);
                return;
            }

            if (v instanceof Number n) {
                cleaned.put(k, n.doubleValue());
                return;
            }

            cleaned.put(k, v.toString());
        });

        return cleaned;
    }

    private void analyzeAndIndexImage(BufferedImage image, String imageName,
                                      Map<String, Object> additionalMetadata) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            String description = analyzeImageWithVision(base64Image);

            Map<String, Object> metadata = new HashMap<>(sanitizeMetadata(additionalMetadata));
            metadata.put("imageName", imageName);
            metadata.put("type", "image");
            metadata.put("width", image.getWidth());
            metadata.put("height", image.getHeight());
            metadata.put("uploadDate", System.currentTimeMillis());
            metadata.put("imageId", UUID.randomUUID().toString());

            TextSegment segment = TextSegment.from(description, Metadata.from(metadata));

            Embedding embedding = embeddingModel.embed(description).content();
            imageStore.add(embedding, segment);

            log.debug("✅ Image indexée: {}", imageName);

        } catch (Exception e) {
            log.error("❌ Erreur analyse image: {}", imageName, e);
        }
    }

    private String analyzeImageWithVision(String base64Image) {
        try {
            UserMessage message = UserMessage.from(
                    TextContent.from(
                            "Décris cette image en détail en français. " +
                                    "Mentionne les objets, les personnes, les couleurs, " +
                                    "le texte visible, le contexte et tout élément important."
                    ),
                    ImageContent.from(base64Image, "image/png")
            );

            ChatRequest request = ChatRequest.builder()
                    .messages(List.<ChatMessage>of(message))
                    .build();

            ChatResponse response = visionModel.chat(request);

            AiMessage ai = response.aiMessage();
            String description = (ai != null && ai.text() != null) ? ai.text() : "";

            log.debug("🤖 Vision AI: {} caractères", description.length());
            return description;

        } catch (Exception e) {
            log.warn("⚠️ Vision AI non disponible: {}", e.getMessage());
            return "Image (analyse Vision AI non disponible)";
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }

        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }

        return filename.substring(lastDot + 1).toLowerCase();
    }
}