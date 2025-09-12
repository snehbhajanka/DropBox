package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SecurityTestsSimple {

    @Test
    public void testPathTraversalInFilename() {
        // Test for path traversal vulnerability in filename
        DropboxApplication app = new DropboxApplication();
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "test.txt", 
            "text/plain", 
            "test content".getBytes()
        );

        // This should be sanitized to prevent path traversal
        String maliciousFilename = "../../../etc/passwd";
        
        ResponseEntity<Map<String,String>> response = app.uploadFile(file, maliciousFilename, null);
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().containsKey("file_id"));
        
        String fileId = response.getBody().get("file_id");
        
        // Download the file and check if filename is sanitized
        ResponseEntity<byte[]> downloadResponse = app.readFile(fileId);
        assertEquals(200, downloadResponse.getStatusCodeValue());
        
        String contentDisposition = downloadResponse.getHeaders().getFirst("Content-Disposition");
        assertNotNull(contentDisposition);
        // Should not contain path traversal characters
        assertFalse(contentDisposition.contains("../"), "Content-Disposition header should not contain path traversal");
    }

    @Test
    public void testHeaderInjectionInContentDisposition() {
        // Upload a file with a malicious filename that could inject headers
        DropboxApplication app = new DropboxApplication();
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "test.txt", 
            "text/plain", 
            "test content".getBytes()
        );

        // Filename with newline characters that could inject headers
        String maliciousFilename = "test.txt\r\nContent-Type: text/html\r\n\r\n<script>alert('xss')</script>";
        
        ResponseEntity<Map<String,String>> response = app.uploadFile(file, maliciousFilename, null);
        assertEquals(200, response.getStatusCodeValue());
        
        String fileId = response.getBody().get("file_id");
        
        // Try to download the file - the header should be sanitized
        ResponseEntity<byte[]> downloadResponse = app.readFile(fileId);
        assertEquals(200, downloadResponse.getStatusCodeValue());
        
        String contentDisposition = downloadResponse.getHeaders().getFirst("Content-Disposition");
        assertNotNull(contentDisposition);
        // Should not contain newline characters that could inject headers
        assertFalse(contentDisposition.contains("\r\n"), "Content-Disposition header should not contain CRLF");
        assertFalse(contentDisposition.contains("\n"), "Content-Disposition header should not contain LF");
    }

    @Test
    public void testLargeFileUpload() {
        // Test for potential DoS through large file uploads
        DropboxApplication app = new DropboxApplication();
        byte[] largeContent = new byte[1024 * 1024]; // 1MB (reduced for test)
        MockMultipartFile largeFile = new MockMultipartFile(
            "file", 
            "large.txt", 
            "text/plain", 
            largeContent
        );

        // This should either be accepted with proper handling or rejected
        ResponseEntity<Map<String,String>> response = app.uploadFile(largeFile, "large.txt", null);
        assertEquals(200, response.getStatusCodeValue());
    }

    @Test
    public void testFileTypeValidation() {
        // Test uploading potentially dangerous file types
        DropboxApplication app = new DropboxApplication();
        MockMultipartFile executableFile = new MockMultipartFile(
            "file", 
            "malware.exe", 
            "application/octet-stream", 
            "fake executable content".getBytes()
        );

        ResponseEntity<Map<String,String>> response = app.uploadFile(executableFile, "malware.exe", null);
        assertEquals(200, response.getStatusCodeValue()); // Current behavior - should be enhanced
    }
}