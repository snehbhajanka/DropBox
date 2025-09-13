package com.dropbox.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {

    private final S3Service s3Service;
    private final String storageType;
    
    // In-memory storage as fallback
    private static final Map<String, FileMetaData> memoryStorage = new HashMap<>();

    @Autowired
    public FileStorageService(S3Service s3Service,
                             @Value("${storage.type:memory}") String storageType) {
        this.s3Service = s3Service;
        this.storageType = storageType;
    }

    /**
     * Store a file using the configured storage backend
     */
    public String storeFile(String fileName, byte[] content, String contentType, Map<String, String> metadata) {
        String fileId = UUID.randomUUID().toString();
        
        if ("s3".equals(storageType) && s3Service.isS3Enabled()) {
            try {
                String s3Key = s3Service.uploadFile(fileName, content, contentType, metadata);
                
                // Store metadata with S3 key reference
                FileMetaData fileMetaData = new FileMetaData(
                    fileId, fileName, LocalDateTime.now(), content.length, contentType, metadata, null
                );
                fileMetaData.setS3Key(s3Key);
                fileMetaData.setStorageType("s3");
                memoryStorage.put(fileId, fileMetaData);
                
                return fileId;
                
            } catch (Exception e) {
                System.err.println("S3 storage failed, falling back to memory: " + e.getMessage());
                // Fall through to memory storage
            }
        }
        
        // Use memory storage
        FileMetaData fileMetaData = new FileMetaData(
            fileId, fileName, LocalDateTime.now(), content.length, contentType, metadata, content
        );
        fileMetaData.setStorageType("memory");
        memoryStorage.put(fileId, fileMetaData);
        
        return fileId;
    }

    /**
     * Retrieve a file
     */
    public FileMetaData getFile(String fileId) {
        FileMetaData metadata = memoryStorage.get(fileId);
        if (metadata == null) {
            return null;
        }
        
        if ("s3".equals(metadata.getStorageType()) && metadata.getS3Key() != null) {
            try {
                byte[] content = s3Service.downloadFile(metadata.getS3Key());
                if (content != null) {
                    metadata.setData(content);
                }
                return metadata;
            } catch (Exception e) {
                System.err.println("Failed to retrieve file from S3: " + e.getMessage());
                return null;
            }
        }
        
        return metadata;
    }

    /**
     * Delete a file
     */
    public boolean deleteFile(String fileId) {
        FileMetaData metadata = memoryStorage.get(fileId);
        if (metadata == null) {
            return false;
        }
        
        if ("s3".equals(metadata.getStorageType()) && metadata.getS3Key() != null) {
            try {
                s3Service.deleteFile(metadata.getS3Key());
            } catch (Exception e) {
                System.err.println("Failed to delete file from S3: " + e.getMessage());
                // Continue with memory cleanup
            }
        }
        
        memoryStorage.remove(fileId);
        return true;
    }

    /**
     * Update file content or metadata
     */
    public boolean updateFile(String fileId, byte[] newContent, Map<String, String> newMetadata) {
        FileMetaData metadata = memoryStorage.get(fileId);
        if (metadata == null) {
            return false;
        }
        
        if (newContent != null) {
            if ("s3".equals(metadata.getStorageType()) && metadata.getS3Key() != null) {
                try {
                    // Delete old version and upload new
                    s3Service.deleteFile(metadata.getS3Key());
                    String newS3Key = s3Service.uploadFile(
                        metadata.getFileName(), newContent, metadata.getContentType(), newMetadata
                    );
                    metadata.setS3Key(newS3Key);
                    metadata.setSize(newContent.length);
                } catch (Exception e) {
                    System.err.println("Failed to update file in S3: " + e.getMessage());
                    // Fall back to memory storage
                    metadata.setData(newContent);
                    metadata.setStorageType("memory");
                    metadata.setS3Key(null);
                }
            } else {
                metadata.setData(newContent);
                metadata.setSize(newContent.length);
            }
        }
        
        if (newMetadata != null) {
            if (metadata.getMetadata() == null) {
                metadata.setMetadata(new HashMap<>());
            }
            metadata.getMetadata().putAll(newMetadata);
        }
        
        return true;
    }

    /**
     * List all files
     */
    public Map<String, FileMetaData> listFiles() {
        return new HashMap<>(memoryStorage);
    }

    /**
     * Get storage statistics
     */
    public Map<String, Object> getStorageStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("storage_type", storageType);
        stats.put("s3_enabled", s3Service.isS3Enabled());
        stats.put("total_files", memoryStorage.size());
        
        long memoryFiles = memoryStorage.values().stream()
            .mapToLong(f -> "memory".equals(f.getStorageType()) ? 1 : 0)
            .sum();
        
        long s3Files = memoryStorage.values().stream()
            .mapToLong(f -> "s3".equals(f.getStorageType()) ? 1 : 0)
            .sum();
        
        stats.put("memory_files", memoryFiles);
        stats.put("s3_files", s3Files);
        
        if (s3Service.isS3Enabled()) {
            try {
                stats.put("s3_security_status", s3Service.getBucketSecurityStatus());
            } catch (Exception e) {
                stats.put("s3_security_status", "error: " + e.getMessage());
            }
        }
        
        return stats;
    }
}