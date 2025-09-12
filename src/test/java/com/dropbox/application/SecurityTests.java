package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SecurityTests {

    private DropboxApplication dropboxApplication = new DropboxApplication();

    @Test
    void testFileNameSanitization() {
        // Test path traversal attempts - these should all fail
        String[] maliciousFileNames = {
            "../../../etc/passwd",
            "..\\..\\windows\\system32\\config\\sam",
            "....//....//etc//passwd",
            "/etc/passwd",
            "\\windows\\system32\\drivers\\etc\\hosts",
            "",
            null
        };

        for (String maliciousFileName : maliciousFileNames) {
            assertThrows(IllegalArgumentException.class, () -> {
                dropboxApplication.sanitizeFileName(maliciousFileName);
            }, "Should reject malicious filename: " + maliciousFileName);
        }
    }

    @Test
    void testValidFileNames() {
        // Test valid file names - these should all pass
        String[] validFileNames = {
            "document.txt",
            "image.jpg",
            "data_file.csv",
            "report-2023.pdf",
            "file123.png"
        };

        for (String validFileName : validFileNames) {
            assertDoesNotThrow(() -> {
                String sanitized = dropboxApplication.sanitizeFileName(validFileName);
                assertEquals(validFileName, sanitized);
            }, "Should accept valid filename: " + validFileName);
        }
    }

    @Test
    void testInvalidCharactersInFileName() {
        String[] invalidFileNames = {
            "file<script>.txt",
            "file|pipe.txt",
            "file\"quote.txt",
            "file:colon.txt",
            "file*star.txt",
            "file?question.txt"
        };

        for (String invalidFileName : invalidFileNames) {
            assertThrows(IllegalArgumentException.class, () -> {
                dropboxApplication.sanitizeFileName(invalidFileName);
            }, "Should reject filename with invalid characters: " + invalidFileName);
        }
    }

    @Test
    void testReservedFileNames() {
        String[] reservedNames = {"CON", "PRN", "AUX", "NUL", "COM1", "LPT1"};

        for (String reservedName : reservedNames) {
            assertThrows(IllegalArgumentException.class, () -> {
                dropboxApplication.sanitizeFileName(reservedName);
            }, "Should reject reserved filename: " + reservedName);
        }
    }

    @Test
    void testFileValidation() {
        // Test file size validation
        byte[] largeFileContent = new byte[11 * 1024 * 1024]; // 11MB - exceeds limit
        MockMultipartFile largeFile = new MockMultipartFile(
            "file", 
            "large.txt", 
            "text/plain", 
            largeFileContent
        );

        assertThrows(IllegalArgumentException.class, () -> {
            dropboxApplication.validateFile(largeFile, "large.txt");
        }, "Should reject files larger than 10MB");

        // Test invalid file type
        MockMultipartFile executableFile = new MockMultipartFile(
            "file", 
            "virus.exe", 
            "application/x-msdownload", 
            "malicious content".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> {
            dropboxApplication.validateFile(executableFile, "virus.exe");
        }, "Should reject executable files");
    }

    @Test
    void testValidFileValidation() {
        // Test valid file
        MockMultipartFile validFile = new MockMultipartFile(
            "file", 
            "document.txt", 
            "text/plain", 
            "valid content".getBytes()
        );

        assertDoesNotThrow(() -> {
            dropboxApplication.validateFile(validFile, "document.txt");
        }, "Should accept valid files");
    }

    @Test
    void testMetadataValidation() {
        // Test metadata with too many entries
        java.util.Map<String, String> tooManyEntries = new java.util.HashMap<>();
        for (int i = 0; i < 15; i++) {
            tooManyEntries.put("key" + i, "value" + i);
        }

        assertThrows(IllegalArgumentException.class, () -> {
            dropboxApplication.validateMetadata(tooManyEntries);
        }, "Should reject metadata with too many entries");

        // Test metadata with malicious content
        java.util.Map<String, String> maliciousMetadata = new java.util.HashMap<>();
        maliciousMetadata.put("script", "<script>alert('xss')</script>");

        assertThrows(IllegalArgumentException.class, () -> {
            dropboxApplication.validateMetadata(maliciousMetadata);
        }, "Should reject metadata with malicious content");

        // Test valid metadata
        java.util.Map<String, String> validMetadata = new java.util.HashMap<>();
        validMetadata.put("author", "John Doe");
        validMetadata.put("category", "documents");

        assertDoesNotThrow(() -> {
            dropboxApplication.validateMetadata(validMetadata);
        }, "Should accept valid metadata");
    }
}