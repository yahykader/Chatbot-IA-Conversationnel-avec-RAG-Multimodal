// ============================================================================
// UTIL - InMemoryMultipartFile.java
// Wrapper MultipartFile pour bytes en mémoire
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Implémentation de MultipartFile pour bytes en mémoire.
 * 
 * Utilisé notamment pour la conversion XLSX → PDF via LibreOffice,
 * où on a besoin de créer un MultipartFile à partir d'un byte array.
 * 
 * Usage :
 * <pre>
 * byte[] pdfBytes = convertedPdf.getBytes();
 * MultipartFile pdfFile = new InMemoryMultipartFile(
 *     "converted.pdf",
 *     "application/pdf",
 *     pdfBytes
 * );
 * </pre>
 */
public class InMemoryMultipartFile implements MultipartFile {
    
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;
    
    /**
     * Constructeur avec tous les paramètres
     */
    public InMemoryMultipartFile(
            String name,
            String originalFilename,
            String contentType,
            byte[] content) {
        
        this.name = name != null ? name : "file";
        this.originalFilename = originalFilename != null ? originalFilename : "file";
        this.contentType = contentType != null ? contentType : "application/octet-stream";
        this.content = content != null ? content : new byte[0];
    }
    
    /**
     * Constructeur simplifié (name = originalFilename)
     */
    public InMemoryMultipartFile(
            String originalFilename,
            String contentType,
            byte[] content) {
        
        this(originalFilename, originalFilename, contentType, content);
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }
    
    @Override
    public String getContentType() {
        return contentType;
    }
    
    @Override
    public boolean isEmpty() {
        return content == null || content.length == 0;
    }
    
    @Override
    public long getSize() {
        return content != null ? content.length : 0;
    }
    
    @Override
    public byte[] getBytes() throws IOException {
        return content;
    }
    
    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(content);
    }
    
    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        throw new UnsupportedOperationException(
            "InMemoryMultipartFile ne supporte pas transferTo(). " +
            "Utilisez getBytes() ou getInputStream() à la place."
        );
    }
}