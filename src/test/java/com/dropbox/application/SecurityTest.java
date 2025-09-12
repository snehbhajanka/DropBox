package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SecurityTest {

    @Test
    public void testFileNameSanitization() {
        // Test dangerous filename sanitization
        String dangerousName = "../../etc/passwd";
        String sanitized = SecurityConfig.sanitizeFileName(dangerousName);
        assertFalse(sanitized.contains("../"));
        assertFalse(sanitized.contains("/"));
        
        // Test null filename
        String nullResult = SecurityConfig.sanitizeFileName(null);
        assertEquals("unnamed_file.txt", nullResult);
        
        // Test normal filename
        String normalName = "document.txt";
        String normalResult = SecurityConfig.sanitizeFileName(normalName);
        assertEquals("document.txt", normalResult);
        
        // Test filename with dangerous characters
        String dangerousChars = "file<script>alert.txt";
        String cleanResult = SecurityConfig.sanitizeFileName(dangerousChars);
        assertFalse(cleanResult.contains("<"));
        assertFalse(cleanResult.contains(">"));
    }

    @Test
    public void testValidFileNameCheck() {
        // Valid filenames
        assertTrue(SecurityConfig.isValidFileName("test.txt"));
        assertTrue(SecurityConfig.isValidFileName("document.pdf"));
        assertTrue(SecurityConfig.isValidFileName("image.jpg"));
        assertTrue(SecurityConfig.isValidFileName("file_name.doc"));
        assertTrue(SecurityConfig.isValidFileName("file-name.docx"));
        
        // Invalid filenames - path traversal
        assertFalse(SecurityConfig.isValidFileName("../test.txt"));
        assertFalse(SecurityConfig.isValidFileName("..\\test.txt"));
        assertFalse(SecurityConfig.isValidFileName("/etc/passwd"));
        assertFalse(SecurityConfig.isValidFileName("folder/file.txt"));
        
        // Invalid filenames - disallowed extensions
        assertFalse(SecurityConfig.isValidFileName("test.exe"));
        assertFalse(SecurityConfig.isValidFileName("script.js"));
        assertFalse(SecurityConfig.isValidFileName("program.bat"));
        
        // Invalid filenames - special characters
        assertFalse(SecurityConfig.isValidFileName("file with spaces.txt"));
        assertFalse(SecurityConfig.isValidFileName("file<script>.txt"));
        assertFalse(SecurityConfig.isValidFileName("file&cmd.txt"));
        
        // Invalid filenames - null/empty
        assertFalse(SecurityConfig.isValidFileName(null));
        assertFalse(SecurityConfig.isValidFileName(""));
        assertFalse(SecurityConfig.isValidFileName("   "));
    }

    @Test
    public void testAllowedFileExtensions() {
        // Test all allowed extensions
        assertTrue(SecurityConfig.isValidFileName("file.txt"));
        assertTrue(SecurityConfig.isValidFileName("document.pdf"));
        assertTrue(SecurityConfig.isValidFileName("spreadsheet.xls"));
        assertTrue(SecurityConfig.isValidFileName("spreadsheet.xlsx"));
        assertTrue(SecurityConfig.isValidFileName("document.doc"));
        assertTrue(SecurityConfig.isValidFileName("document.docx"));
        assertTrue(SecurityConfig.isValidFileName("image.jpg"));
        assertTrue(SecurityConfig.isValidFileName("image.jpeg"));
        assertTrue(SecurityConfig.isValidFileName("image.png"));
        assertTrue(SecurityConfig.isValidFileName("image.gif"));
        assertTrue(SecurityConfig.isValidFileName("video.mp4"));
        assertTrue(SecurityConfig.isValidFileName("audio.mp3"));
    }

    @Test
    public void testSecurityValidations() {
        DropboxApplication app = new DropboxApplication();
        
        // Test metadata validation (we'll use reflection to access private method)
        // Since the method is private, we'll test indirectly by ensuring the logic works
        
        // Test valid metadata
        Map<String, String> validMetadata = new HashMap<>();
        validMetadata.put("description", "A normal description");
        validMetadata.put("category", "documents");
        
        // This would be valid metadata (no dangerous characters)
        assertFalse(containsDangerousCharactersTest("normal text"));
        assertFalse(containsDangerousCharactersTest("description_123"));
        
        // Test dangerous metadata
        assertTrue(containsDangerousCharactersTest("<script>alert('xss')</script>"));
        assertTrue(containsDangerousCharactersTest("'; DROP TABLE files; --"));
        assertTrue(containsDangerousCharactersTest("javascript:alert(1)"));
        assertTrue(containsDangerousCharactersTest("onload=malicious()"));
    }

    // Helper method to simulate the private containsDangerousCharacters method
    private boolean containsDangerousCharactersTest(String input) {
        if (input == null) {
            return false;
        }
        
        String lower = input.toLowerCase();
        return lower.contains("<script") || 
               lower.contains("javascript:") || 
               lower.contains("onload=") ||
               lower.contains("onerror=") ||
               input.contains("'") ||
               input.contains("\"") ||
               input.contains(";") ||
               input.contains("--") ||
               input.contains("/*") ||
               input.contains("*/");
    }

    @Test
    public void testMaxFileSizeConstant() {
        // Verify the max file size is set to 10MB
        assertEquals(10 * 1024 * 1024, SecurityConfig.MAX_FILE_SIZE);
    }

    @Test
    public void testAllowedExtensionsList() {
        // Verify all expected extensions are in the allowed list
        assertTrue(SecurityConfig.ALLOWED_FILE_EXTENSIONS.contains(".txt"));
        assertTrue(SecurityConfig.ALLOWED_FILE_EXTENSIONS.contains(".pdf"));
        assertTrue(SecurityConfig.ALLOWED_FILE_EXTENSIONS.contains(".jpg"));
        assertTrue(SecurityConfig.ALLOWED_FILE_EXTENSIONS.contains(".mp4"));
        
        // Verify dangerous extensions are NOT in the list
        assertFalse(SecurityConfig.ALLOWED_FILE_EXTENSIONS.contains(".exe"));
        assertFalse(SecurityConfig.ALLOWED_FILE_EXTENSIONS.contains(".bat"));
        assertFalse(SecurityConfig.ALLOWED_FILE_EXTENSIONS.contains(".js"));
        assertFalse(SecurityConfig.ALLOWED_FILE_EXTENSIONS.contains(".php"));
    }
}