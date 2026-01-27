package com.exemple.transactionservice.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

    /**
     * MultipartFile qui persiste sur le disque pour traitement asynchrone
     * Résout le problème de NoSuchFileException quand Tomcat supprime les fichiers temporaires.
    */
public class PersistentMultipartFile implements MultipartFile {
        
        private final Path filePath;
        private final String originalFilename;
        private final String contentType;
        
        public PersistentMultipartFile(Path filePath, String originalFilename, String contentType) {
            this.filePath = filePath;
            this.originalFilename = originalFilename;
            this.contentType = contentType != null ? contentType : determineContentType(originalFilename);
        }
        
        @Override
        public String getName() {
            return originalFilename;
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
            try {
                return Files.size(filePath) == 0;
            } catch (IOException e) {
                return true;
            }
        }
        
        @Override
        public long getSize() {
            try {
                return Files.size(filePath);
            } catch (IOException e) {
                return 0;
            }
        }
        
        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(filePath);
        }
        
        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(filePath);
        }
        
        @Override
        public void transferTo(File dest) throws IOException {
            Files.copy(filePath, dest.toPath());
        }
        
        private String determineContentType(String filename) {
            if (filename == null) return "application/octet-stream";
            
            String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
            return switch (extension) {
                case "pdf" -> "application/pdf";
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "doc" -> "application/msword";
                case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "xls" -> "application/vnd.ms-excel";
                case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                case "ppt" -> "application/vnd.ms-powerpoint";
                case "png" -> "image/png";
                case "jpg", "jpeg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "txt" -> "text/plain";
                case "csv" -> "text/csv";
                default -> "application/octet-stream";
            };
        }
}