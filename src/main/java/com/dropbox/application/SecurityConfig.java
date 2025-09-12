package com.dropbox.application;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Configuration
public class SecurityConfig implements WebMvcConfigurer {

    // Allowed file extensions for security
    public static final List<String> ALLOWED_FILE_EXTENSIONS = Arrays.asList(
        ".txt", ".pdf", ".doc", ".docx", ".xls", ".xlsx", 
        ".jpg", ".jpeg", ".png", ".gif", ".mp4", ".mp3"
    );

    // Maximum file size (10MB)
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    // Pattern for safe file names (alphanumeric, dots, hyphens, underscores)
    public static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SecurityHeadersInterceptor());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(false);
    }

    /**
     * Validates if a filename is safe to use
     */
    public static boolean isValidFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        // Check for path traversal attempts
        if (fileName.contains("../") || fileName.contains("..\\") || 
            fileName.contains("/") || fileName.contains("\\")) {
            return false;
        }
        
        // Check filename pattern
        if (!SAFE_FILENAME_PATTERN.matcher(fileName).matches()) {
            return false;
        }
        
        // Check file extension
        String lowerFileName = fileName.toLowerCase();
        return ALLOWED_FILE_EXTENSIONS.stream()
                .anyMatch(ext -> lowerFileName.endsWith(ext));
    }

    /**
     * Sanitizes filename by removing potentially dangerous characters
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "unnamed_file.txt";
        }
        
        // Remove path separators and dangerous characters
        String sanitized = fileName.replaceAll("[/\\\\:*?\"<>|]", "_");
        
        // Limit length
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }
        
        return sanitized.trim();
    }

    /**
     * Interceptor to add security headers to all responses
     */
    public static class SecurityHeadersInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            // Add security headers
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("X-XSS-Protection", "1; mode=block");
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            response.setHeader("Content-Security-Policy", "default-src 'self'");
            
            return true;
        }
    }
}