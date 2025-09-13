package com.dropbox.application.service;

import com.dropbox.application.FileMetaData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory implementation of FileStorageService.
 * This is the default storage when S3 is not configured.
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryStorageService implements FileStorageService {

    private final Map<String, FileMetaData> fileStorage = new HashMap<>();

    @Override
    public void storeFile(String fileId, FileMetaData fileMetaData) {
        fileStorage.put(fileId, fileMetaData);
    }

    @Override
    public FileMetaData getFile(String fileId) {
        return fileStorage.get(fileId);
    }

    @Override
    public boolean deleteFile(String fileId) {
        return fileStorage.remove(fileId) != null;
    }

    @Override
    public Collection<FileMetaData> listFiles() {
        return fileStorage.values();
    }

    @Override
    public boolean fileExists(String fileId) {
        return fileStorage.containsKey(fileId);
    }

    @Override
    public void updateFile(String fileId, FileMetaData fileMetaData) {
        if (fileStorage.containsKey(fileId)) {
            fileStorage.put(fileId, fileMetaData);
        }
    }
}