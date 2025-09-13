package com.dropbox.application.service;

import com.dropbox.application.FileMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "storage.type=memory"
})
class InMemoryStorageServiceTest {

    private InMemoryStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new InMemoryStorageService();
    }

    @Test
    void testUploadFile() {
        byte[] fileData = "Test file content".getBytes();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("author", "test-user");
        
        String fileId = storageService.uploadFile(fileData, "test.txt", "text/plain", metadata);
        
        assertNotNull(fileId);
        assertFalse(fileId.isEmpty());
    }

    @Test
    void testGetFile() {
        byte[] fileData = "Test file content".getBytes();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("author", "test-user");
        
        String fileId = storageService.uploadFile(fileData, "test.txt", "text/plain", metadata);
        FileMetaData retrievedFile = storageService.getFile(fileId);
        
        assertNotNull(retrievedFile);
        assertEquals(fileId, retrievedFile.getFileID());
        assertEquals("test.txt", retrievedFile.getFileName());
        assertEquals("text/plain", retrievedFile.getContentType());
        assertArrayEquals(fileData, retrievedFile.getData());
        assertEquals("test-user", retrievedFile.getMetadata().get("author"));
    }

    @Test
    void testGetNonExistentFile() {
        FileMetaData file = storageService.getFile("non-existent-id");
        assertNull(file);
    }

    @Test
    void testListFiles() {
        // Initially empty
        Collection<FileMetaData> files = storageService.listFiles();
        assertTrue(files.isEmpty());
        
        // Add files
        storageService.uploadFile("Content 1".getBytes(), "file1.txt", "text/plain", null);
        storageService.uploadFile("Content 2".getBytes(), "file2.txt", "text/plain", null);
        
        files = storageService.listFiles();
        assertEquals(2, files.size());
    }

    @Test
    void testDeleteFile() {
        byte[] fileData = "Test file content".getBytes();
        String fileId = storageService.uploadFile(fileData, "test.txt", "text/plain", null);
        
        // Verify file exists
        assertNotNull(storageService.getFile(fileId));
        
        // Delete file
        boolean deleted = storageService.deleteFile(fileId);
        assertTrue(deleted);
        
        // Verify file is gone
        assertNull(storageService.getFile(fileId));
    }

    @Test
    void testDeleteNonExistentFile() {
        boolean deleted = storageService.deleteFile("non-existent-id");
        assertFalse(deleted);
    }

    @Test
    void testUpdateFile() {
        byte[] originalData = "Original content".getBytes();
        Map<String, String> originalMetadata = new HashMap<>();
        originalMetadata.put("version", "1");
        
        String fileId = storageService.uploadFile(originalData, "test.txt", "text/plain", originalMetadata);
        
        // Update with new data and metadata
        byte[] newData = "Updated content".getBytes();
        Map<String, String> newMetadata = new HashMap<>();
        newMetadata.put("version", "2");
        newMetadata.put("editor", "test-user");
        
        boolean updated = storageService.updateFile(fileId, newData, "text/plain", newMetadata);
        assertTrue(updated);
        
        // Verify updates
        FileMetaData updatedFile = storageService.getFile(fileId);
        assertNotNull(updatedFile);
        assertArrayEquals(newData, updatedFile.getData());
        assertEquals("2", updatedFile.getMetadata().get("version"));
        assertEquals("test-user", updatedFile.getMetadata().get("editor"));
    }

    @Test
    void testUpdateNonExistentFile() {
        byte[] data = "Some content".getBytes();
        boolean updated = storageService.updateFile("non-existent-id", data, "text/plain", null);
        assertFalse(updated);
    }

    @Test
    void testUpdateFileWithNullData() {
        byte[] originalData = "Original content".getBytes();
        String fileId = storageService.uploadFile(originalData, "test.txt", "text/plain", null);
        
        // Update only metadata
        Map<String, String> newMetadata = new HashMap<>();
        newMetadata.put("updated", "true");
        boolean updated = storageService.updateFile(fileId, null, null, newMetadata);
        assertTrue(updated);
        
        // Verify original data is preserved but metadata is updated
        FileMetaData updatedFile = storageService.getFile(fileId);
        assertNotNull(updatedFile);
        assertArrayEquals(originalData, updatedFile.getData());
        assertEquals("true", updatedFile.getMetadata().get("updated"));
    }
}