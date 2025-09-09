package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileControllerTest {

    private final FileController fileController = new FileController();

    @Test
    void listFiles_ShouldReturnValidResponse() {
        ResponseEntity<Map<String, Object>> response = fileController.listFiles();
        
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        // The response will contain either files or status, both are valid
        assertTrue(response.getBody().containsKey("files") || response.getBody().containsKey("status"));
    }

    @Test
    void uploadFile_WithValidInput_ShouldReturnFileId() {
        MockMultipartFile file = new MockMultipartFile(
                "file", 
                "test.txt", 
                "text/plain", 
                "Hello, World!".getBytes()
        );

        ResponseEntity<Map<String, String>> response = fileController.uploadFile(file, "test-file.txt", null);
        
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("file_id"));
        assertNotNull(response.getBody().get("file_id"));
    }

    @Test
    void uploadFile_WithEmptyFile_ShouldReturnBadRequest() {
        MockMultipartFile file = new MockMultipartFile(
                "file", 
                "test.txt", 
                "text/plain", 
                new byte[0]
        );

        ResponseEntity<Map<String, String>> response = fileController.uploadFile(file, "test-file.txt", null);
        
        assertEquals(400, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
        assertEquals("File is required and cannot be empty", response.getBody().get("error"));
    }

    @Test
    void uploadFile_WithEmptyFileName_ShouldReturnBadRequest() {
        MockMultipartFile file = new MockMultipartFile(
                "file", 
                "test.txt", 
                "text/plain", 
                "Hello, World!".getBytes()
        );

        ResponseEntity<Map<String, String>> response = fileController.uploadFile(file, "", null);
        
        assertEquals(400, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
        assertEquals("File name is required and cannot be empty", response.getBody().get("error"));
    }

    @Test
    void readFile_WithNonExistentId_ShouldReturnNotFound() {
        ResponseEntity<byte[]> response = fileController.readFile("non-existent-id");
        
        assertEquals(404, response.getStatusCodeValue());
    }

    @Test
    void deleteFile_WithNonExistentId_ShouldReturnNotFound() {
        ResponseEntity<?> response = fileController.deleteFile("non-existent-id");
        
        assertEquals(404, response.getStatusCodeValue());
    }

    @Test
    void updateFile_WithNonExistentId_ShouldReturnNotFound() {
        ResponseEntity<?> response = fileController.updateFile("non-existent-id", null, null);
        
        assertEquals(404, response.getStatusCodeValue());
    }
}