package com.dropbox.application.service;

import com.dropbox.application.FileMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FileStorageService implementations.
 * These tests ensure that storage services work correctly with security configurations.
 */
@SpringBootTest
class FileStorageServiceIntegrationTest {

    private InMemoryStorageService inMemoryStorageService;
    private FileMetaData testFileMetaData;

    @BeforeEach
    void setUp() {
        inMemoryStorageService = new InMemoryStorageService();
        
        // Create test file metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("category", "test");
        metadata.put("owner", "junit");
        
        testFileMetaData = new FileMetaData(
                "test-file-id",
                "test.txt",
                LocalDateTime.now(),
                100L,
                "text/plain",
                metadata,
                "test content".getBytes()
        );
    }

    /**
     * Test that in-memory storage works correctly as fallback
     */
    @Test
    void testInMemoryStorageBasicOperations() {
        // Store file
        inMemoryStorageService.storeFile("test-id", testFileMetaData);
        
        // Verify file exists
        assertTrue(inMemoryStorageService.fileExists("test-id"));
        
        // Retrieve file
        FileMetaData retrieved = inMemoryStorageService.getFile("test-id");
        assertNotNull(retrieved);
        assertEquals("test.txt", retrieved.getFileName());
        assertEquals("text/plain", retrieved.getContentType());
        
        // List files
        var files = inMemoryStorageService.listFiles();
        assertEquals(1, files.size());
        
        // Update file
        testFileMetaData.setFileName("updated.txt");
        inMemoryStorageService.updateFile("test-id", testFileMetaData);
        retrieved = inMemoryStorageService.getFile("test-id");
        assertEquals("updated.txt", retrieved.getFileName());
        
        // Delete file
        boolean deleted = inMemoryStorageService.deleteFile("test-id");
        assertTrue(deleted);
        assertFalse(inMemoryStorageService.fileExists("test-id"));
    }

    /**
     * Test that file metadata is serializable for S3 storage
     */
    @Test
    void testFileMetaDataIsSerializable() {
        // This test ensures that FileMetaData can be serialized for S3 storage
        assertDoesNotThrow(() -> {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
            oos.writeObject(testFileMetaData);
            oos.close();
            
            byte[] serialized = baos.toByteArray();
            assertTrue(serialized.length > 0, "FileMetaData should be serializable");
            
            // Test deserialization
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(serialized);
            java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais);
            FileMetaData deserialized = (FileMetaData) ois.readObject();
            ois.close();
            
            assertEquals(testFileMetaData.getFileID(), deserialized.getFileID());
            assertEquals(testFileMetaData.getFileName(), deserialized.getFileName());
        });
    }

    /**
     * Test that storage service interface is properly implemented
     */
    @Test
    void testStorageServiceInterface() {
        FileStorageService storageService = inMemoryStorageService;
        
        // Test all interface methods are available
        assertDoesNotThrow(() -> {
            storageService.storeFile("test", testFileMetaData);
            storageService.getFile("test");
            storageService.fileExists("test");
            storageService.listFiles();
            storageService.updateFile("test", testFileMetaData);
            storageService.deleteFile("test");
        });
    }

    /**
     * Test handling of non-existent files
     */
    @Test
    void testNonExistentFileHandling() {
        // Getting non-existent file should return null
        assertNull(inMemoryStorageService.getFile("non-existent"));
        
        // Checking existence of non-existent file should return false
        assertFalse(inMemoryStorageService.fileExists("non-existent"));
        
        // Deleting non-existent file should return false
        assertFalse(inMemoryStorageService.deleteFile("non-existent"));
        
        // Updating non-existent file should not throw exception
        assertDoesNotThrow(() -> {
            inMemoryStorageService.updateFile("non-existent", testFileMetaData);
        });
    }

    /**
     * Test storage with various file types and sizes
     */
    @Test
    void testStorageWithVariousFileTypes() {
        // Test different content types
        String[] contentTypes = {"text/plain", "image/jpeg", "application/pdf", "video/mp4"};
        
        for (int i = 0; i < contentTypes.length; i++) {
            FileMetaData fileData = new FileMetaData(
                    "file-" + i,
                    "test" + i + ".ext",
                    LocalDateTime.now(),
                    1024L * (i + 1),
                    contentTypes[i],
                    new HashMap<>(),
                    ("content " + i).getBytes()
            );
            
            inMemoryStorageService.storeFile("file-" + i, fileData);
            
            FileMetaData retrieved = inMemoryStorageService.getFile("file-" + i);
            assertEquals(contentTypes[i], retrieved.getContentType());
        }
        
        // Verify all files are stored
        assertEquals(4, inMemoryStorageService.listFiles().size());
    }
}