package com.dropbox.application.service;

import com.dropbox.application.FileMetaData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryStorageService implements StorageService {
    
    private final Map<String, FileMetaData> fileStorage = new HashMap<>();
    
    @Override
    public String uploadFile(byte[] fileData, String fileName, String contentType, Map<String, String> metadata) {
        String fileId = UUID.randomUUID().toString();
        FileMetaData fileMetaData = new FileMetaData(fileId, fileName, LocalDateTime.now(), 
                fileData.length, contentType, metadata, fileData);
        fileStorage.put(fileId, fileMetaData);
        return fileId;
    }
    
    @Override
    public FileMetaData getFile(String fileId) {
        return fileStorage.get(fileId);
    }
    
    @Override
    public Collection<FileMetaData> listFiles() {
        return fileStorage.values();
    }
    
    @Override
    public boolean deleteFile(String fileId) {
        return fileStorage.remove(fileId) != null;
    }
    
    @Override
    public boolean updateFile(String fileId, byte[] fileData, String contentType, Map<String, String> metadata) {
        FileMetaData existingFile = fileStorage.get(fileId);
        if (existingFile != null) {
            if (fileData != null) {
                existingFile.setData(fileData);
                existingFile.setSize(fileData.length);
                existingFile.setContentType(contentType);
            }
            if (metadata != null) {
                existingFile.getMetadata().putAll(metadata);
            }
            return true;
        }
        return false;
    }
}