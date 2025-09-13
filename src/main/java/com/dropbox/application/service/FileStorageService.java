package com.dropbox.application.service;

import com.dropbox.application.FileMetaData;

import java.util.Collection;
import java.util.Map;

/**
 * Common interface for file storage services.
 * This allows switching between in-memory and S3 storage implementations.
 */
public interface FileStorageService {
    
    /**
     * Store a file with metadata
     */
    void storeFile(String fileId, FileMetaData fileMetaData);
    
    /**
     * Retrieve a file by ID
     */
    FileMetaData getFile(String fileId);
    
    /**
     * Delete a file by ID
     */
    boolean deleteFile(String fileId);
    
    /**
     * List all files
     */
    Collection<FileMetaData> listFiles();
    
    /**
     * Check if a file exists
     */
    boolean fileExists(String fileId);
    
    /**
     * Update file metadata
     */
    void updateFile(String fileId, FileMetaData fileMetaData);
}