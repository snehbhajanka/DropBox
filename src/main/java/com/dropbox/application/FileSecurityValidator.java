package com.dropbox.application;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class FileSecurityValidator {
    
    // Maximum file size: 10MB
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    
    // Allowed file extensions (whitelist approach)
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
        "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", 
        "jpg", "jpeg", "png", "gif", "bmp", "zip", "rar", "csv"
    ));
    
    // Allowed MIME types (additional validation)
    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(Arrays.asList(
        "text/plain", "application/pdf", "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "image/jpeg", "image/png", "image/gif", "image/bmp",
        "application/zip", "application/x-rar-compressed", "text/csv"
    ));
    
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Validates file upload for security compliance
     */
    public static ValidationResult validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new ValidationResult(false, "File is empty or null");
        }
        
        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            return new ValidationResult(false, "File size exceeds maximum allowed size (10MB)");
        }
        
        // Validate original filename
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            return new ValidationResult(false, "Invalid filename");
        }
        
        // Check for path traversal attempts
        if (containsPathTraversal(originalFilename)) {
            return new ValidationResult(false, "Invalid filename: path traversal detected");
        }
        
        // Validate file extension
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return new ValidationResult(false, "File type not allowed: " + extension);
        }
        
        // Validate MIME type
        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
            return new ValidationResult(false, "MIME type not allowed: " + mimeType);
        }
        
        return new ValidationResult(true, null);
    }
    
    /**
     * Sanitizes filename to prevent path traversal and other security issues
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "unknown_file";
        }
        
        // Remove path components and normalize
        String sanitized = Paths.get(filename).getFileName().toString();
        
        // Remove dangerous characters
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        // Prevent empty or dot-only filenames
        if (sanitized.trim().isEmpty() || sanitized.matches("^\\.+$")) {
            sanitized = "unknown_file";
        }
        
        // Limit length
        if (sanitized.length() > 255) {
            String extension = getFileExtension(sanitized);
            String nameWithoutExt = sanitized.substring(0, sanitized.lastIndexOf('.'));
            sanitized = nameWithoutExt.substring(0, 250 - extension.length()) + "." + extension;
        }
        
        return sanitized;
    }
    
    /**
     * Validates string input to prevent injection attacks
     */
    public static ValidationResult validateStringInput(String input, String fieldName, int maxLength) {
        if (input == null) {
            return new ValidationResult(true, null); // null is acceptable for optional fields
        }
        
        if (input.length() > maxLength) {
            return new ValidationResult(false, fieldName + " exceeds maximum length (" + maxLength + ")");
        }
        
        // Check for potentially dangerous characters/patterns
        if (input.contains("<script") || input.contains("javascript:") || 
            input.contains("../") || input.contains("..\\")) {
            return new ValidationResult(false, fieldName + " contains invalid characters");
        }
        
        return new ValidationResult(true, null);
    }
    
    private static boolean containsPathTraversal(String filename) {
        String normalized = filename.toLowerCase();
        return normalized.contains("../") || normalized.contains("..\\") ||
               normalized.contains("/") || normalized.contains("\\") ||
               normalized.startsWith(".") || normalized.contains(":");
    }
    
    private static String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }
}