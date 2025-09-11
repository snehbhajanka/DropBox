package com.dropbox.application;

import java.util.regex.Pattern;

public class SecurityUtils {
    
    // Pattern to detect CRLF injection attempts
    private static final Pattern CRLF_PATTERN = Pattern.compile("[\r\n]");
    
    // Pattern to detect path traversal attempts
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile("(\\.\\.[\\\\/]|[\\\\/]\\.\\.|\\.\\.)");
    
    // Pattern for valid filename characters (alphanumeric, dots, dashes, underscores, spaces)
    private static final Pattern VALID_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._\\-\\s]+$");
    
    /**
     * Sanitizes filename to prevent header injection attacks
     * @param filename Original filename
     * @return Sanitized filename safe for HTTP headers
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "unnamed_file";
        }
        
        // Remove CRLF characters that could lead to header injection
        String sanitized = CRLF_PATTERN.matcher(filename).replaceAll("");
        
        // Remove path traversal sequences
        sanitized = PATH_TRAVERSAL_PATTERN.matcher(sanitized).replaceAll("");
        
        // Replace any remaining invalid characters with underscores
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._\\-\\s]", "_");
        
        // Trim whitespace and ensure it's not empty
        sanitized = sanitized.trim();
        if (sanitized.isEmpty()) {
            sanitized = "unnamed_file";
        }
        
        // Limit length to prevent extremely long filenames
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }
        
        return sanitized;
    }
    
    /**
     * Validates if a filename is safe (no path traversal or injection attempts)
     * @param filename Filename to validate
     * @return true if filename is safe, false otherwise
     */
    public static boolean isValidFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return false;
        }
        
        // Check for CRLF injection
        if (CRLF_PATTERN.matcher(filename).find()) {
            return false;
        }
        
        // Check for path traversal
        if (PATH_TRAVERSAL_PATTERN.matcher(filename).find()) {
            return false;
        }
        
        // Check if filename contains only valid characters
        return VALID_FILENAME_PATTERN.matcher(filename.trim()).matches();
    }
    
    /**
     * Validates file size to prevent memory exhaustion
     * @param fileSize Size of the file in bytes
     * @param maxSizeBytes Maximum allowed file size in bytes
     * @return true if file size is acceptable, false otherwise
     */
    public static boolean isValidFileSize(long fileSize, long maxSizeBytes) {
        return fileSize > 0 && fileSize <= maxSizeBytes;
    }
}