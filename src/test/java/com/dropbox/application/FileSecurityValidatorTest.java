package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileSecurityValidatorTest {

    @Test
    void testValidFile() {
        MockMultipartFile validFile = new MockMultipartFile(
            "file", "test.txt", "text/plain", "Hello World".getBytes());
        
        FileSecurityValidator.ValidationResult result = FileSecurityValidator.validateFile(validFile);
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
    }

    @Test
    void testFileTooLarge() {
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile largeFile = new MockMultipartFile(
            "file", "large.txt", "text/plain", largeContent);
        
        FileSecurityValidator.ValidationResult result = FileSecurityValidator.validateFile(largeFile);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("size exceeds"));
    }

    @Test
    void testInvalidFileExtension() {
        MockMultipartFile invalidFile = new MockMultipartFile(
            "file", "malicious.exe", "application/octet-stream", "malicious".getBytes());
        
        FileSecurityValidator.ValidationResult result = FileSecurityValidator.validateFile(invalidFile);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("File type not allowed"));
    }

    @Test
    void testInvalidMimeType() {
        MockMultipartFile invalidFile = new MockMultipartFile(
            "file", "test.txt", "application/x-executable", "content".getBytes());
        
        FileSecurityValidator.ValidationResult result = FileSecurityValidator.validateFile(invalidFile);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("MIME type not allowed"));
    }

    @Test
    void testPathTraversalInFilename() {
        MockMultipartFile traversalFile = new MockMultipartFile(
            "file", "../../../etc/passwd", "text/plain", "content".getBytes());
        
        FileSecurityValidator.ValidationResult result = FileSecurityValidator.validateFile(traversalFile);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("path traversal"));
    }

    @Test
    void testEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file", "empty.txt", "text/plain", new byte[0]);
        
        FileSecurityValidator.ValidationResult result = FileSecurityValidator.validateFile(emptyFile);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("empty"));
    }

    @Test
    void testNullFile() {
        FileSecurityValidator.ValidationResult result = FileSecurityValidator.validateFile(null);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("empty or null"));
    }

    @Test
    void testSanitizeFilename() {
        assertEquals("test.txt", FileSecurityValidator.sanitizeFilename("test.txt"));
        assertEquals("test_file.txt", FileSecurityValidator.sanitizeFilename("../test file.txt"));
        assertEquals("normal_filename.pdf", FileSecurityValidator.sanitizeFilename("normal filename.pdf"));
        assertEquals("unknown_file", FileSecurityValidator.sanitizeFilename(""));
        assertEquals("unknown_file", FileSecurityValidator.sanitizeFilename(null));
        assertEquals("script_.txt", FileSecurityValidator.sanitizeFilename("<script>test</script>.txt"));
    }

    @Test
    void testValidateStringInput() {
        // Valid input
        FileSecurityValidator.ValidationResult result = 
            FileSecurityValidator.validateStringInput("valid input", "test", 50);
        assertTrue(result.isValid());

        // Null input (should be valid for optional fields)
        result = FileSecurityValidator.validateStringInput(null, "test", 50);
        assertTrue(result.isValid());

        // Too long input
        String longString = "a".repeat(51);
        result = FileSecurityValidator.validateStringInput(longString, "test", 50);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("exceeds maximum length"));

        // Dangerous content
        result = FileSecurityValidator.validateStringInput("<script>alert('xss')</script>", "test", 100);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("invalid characters"));

        // Path traversal
        result = FileSecurityValidator.validateStringInput("../../../etc/passwd", "test", 100);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("invalid characters"));
    }

    @Test
    void testAllowedFileTypes() {
        String[] allowedTypes = {"txt", "pdf", "doc", "docx", "jpg", "png", "zip"};
        String[] allowedMimes = {
            "text/plain", "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/jpeg", "image/png", "application/zip"
        };

        for (int i = 0; i < allowedTypes.length && i < allowedMimes.length; i++) {
            MockMultipartFile file = new MockMultipartFile(
                "file", "test." + allowedTypes[i], allowedMimes[i], "content".getBytes());
            
            FileSecurityValidator.ValidationResult result = FileSecurityValidator.validateFile(file);
            assertTrue(result.isValid(), "File type " + allowedTypes[i] + " should be allowed");
        }
    }
}