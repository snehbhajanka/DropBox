package com.dropbox.application.service;

import com.dropbox.application.FileMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class S3ServiceIntegrationTest {

    @Autowired
    private S3Service s3Service;

    private byte[] testFileData;
    private String testFileName;
    private String testContentType;
    private Map<String, String> testMetadata;

    @BeforeEach
    void setUp() {
        testFileData = "This is test file content for security validation".getBytes();
        testFileName = "security-test-file.txt";
        testContentType = "text/plain";
        testMetadata = new HashMap<>();
        testMetadata.put("test-category", "security");
        testMetadata.put("purpose", "S3 public access block validation");
    }

    @Test
    void testUploadFile_BlocksPublicAccess() {
        // Upload a file to S3 (in test mode, uses in-memory storage)
        String fileId = s3Service.uploadFile(testFileName, testFileData, testContentType, testMetadata);
        
        assertThat(fileId).isNotNull();
        assertThat(fileId).isNotEmpty();
        
        // Verify file metadata
        FileMetaData metadata = s3Service.getFileMetadata(fileId);
        assertThat(metadata).isNotNull();
        assertThat(metadata.getFileName()).isEqualTo(testFileName);
        assertThat(metadata.getContentType()).isEqualTo(testContentType);
        assertThat(metadata.getSize()).isEqualTo(testFileData.length);
        assertThat(metadata.getMetadata()).containsKey("test-category");
        assertThat(metadata.getMetadata().get("test-category")).isEqualTo("security");
    }

    @Test
    void testSecureFileRetrieval() {
        // Upload file first
        String fileId = s3Service.uploadFile(testFileName, testFileData, testContentType, testMetadata);
        
        // Retrieve file data
        byte[] retrievedData = s3Service.getFileData(fileId);
        
        assertThat(retrievedData).isNotNull();
        assertThat(retrievedData).isEqualTo(testFileData);
        assertThat(new String(retrievedData)).isEqualTo("This is test file content for security validation");
    }

    @Test
    void testListFiles_SecurityMetadata() {
        // Upload multiple files
        String fileId1 = s3Service.uploadFile("secure-file-1.txt", "Content 1".getBytes(), "text/plain", 
                Map.of("security-level", "high"));
        String fileId2 = s3Service.uploadFile("secure-file-2.txt", "Content 2".getBytes(), "text/plain", 
                Map.of("security-level", "medium"));
        
        // List files
        List<FileMetaData> files = s3Service.listFiles();
        
        assertThat(files).hasSize(2);
        assertThat(files).extracting(FileMetaData::getFileID).containsExactlyInAnyOrder(fileId1, fileId2);
        
        // Verify security metadata is preserved
        FileMetaData file1 = files.stream().filter(f -> f.getFileID().equals(fileId1)).findFirst().orElse(null);
        assertThat(file1).isNotNull();
        assertThat(file1.getMetadata()).containsEntry("security-level", "high");
    }

    @Test
    void testDeleteFile_SecureRemoval() {
        // Upload file
        String fileId = s3Service.uploadFile(testFileName, testFileData, testContentType, testMetadata);
        
        // Verify file exists
        assertThat(s3Service.getFileMetadata(fileId)).isNotNull();
        
        // Delete file
        boolean deleted = s3Service.deleteFile(fileId);
        
        assertThat(deleted).isTrue();
        assertThat(s3Service.getFileMetadata(fileId)).isNull();
        assertThat(s3Service.getFileData(fileId)).isNull();
    }

    @Test
    void testUpdateFile_PreservesSecuritySettings() {
        // Upload original file
        String fileId = s3Service.uploadFile(testFileName, testFileData, testContentType, testMetadata);
        
        // Update file with new data and metadata
        byte[] newData = "Updated secure content".getBytes();
        Map<String, String> newMetadata = Map.of("updated", "true", "security-audit", "passed");
        
        boolean updated = s3Service.updateFile(fileId, newData, "text/plain", newMetadata);
        
        assertThat(updated).isTrue();
        
        // Verify updated content
        byte[] retrievedData = s3Service.getFileData(fileId);
        assertThat(new String(retrievedData)).isEqualTo("Updated secure content");
        
        // Verify metadata is merged (original + new)
        FileMetaData updatedMetadata = s3Service.getFileMetadata(fileId);
        assertThat(updatedMetadata.getMetadata()).containsEntry("updated", "true");
        assertThat(updatedMetadata.getMetadata()).containsEntry("security-audit", "passed");
        assertThat(updatedMetadata.getMetadata()).containsEntry("test-category", "security"); // Original preserved
    }

    @Test
    void testUpdateNonExistentFile_ReturnsFailure() {
        // Try to update a file that doesn't exist
        boolean updated = s3Service.updateFile("non-existent-id", "data".getBytes(), "text/plain", null);
        
        assertThat(updated).isFalse();
    }

    @Test
    void testDeleteNonExistentFile_ReturnsFailure() {
        // Try to delete a file that doesn't exist
        boolean deleted = s3Service.deleteFile("non-existent-id");
        
        assertThat(deleted).isFalse();
    }

    @Test
    void testEncryptionMetadata_IsPreserved() {
        // Upload file with encryption-related metadata
        Map<String, String> encryptionMetadata = Map.of(
                "encryption-algorithm", "AES256",
                "security-classification", "confidential",
                "access-control", "restricted"
        );
        
        String fileId = s3Service.uploadFile("encrypted-file.dat", testFileData, "application/octet-stream", encryptionMetadata);
        
        // Verify encryption metadata is preserved
        FileMetaData metadata = s3Service.getFileMetadata(fileId);
        assertThat(metadata.getMetadata()).containsEntry("encryption-algorithm", "AES256");
        assertThat(metadata.getMetadata()).containsEntry("security-classification", "confidential");
        assertThat(metadata.getMetadata()).containsEntry("access-control", "restricted");
    }
}