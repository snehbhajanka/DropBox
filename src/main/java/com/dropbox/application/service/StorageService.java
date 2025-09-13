package com.dropbox.application.service;

import com.dropbox.application.FileMetaData;

import java.util.Collection;
import java.util.Map;

public interface StorageService {
    
    String uploadFile(byte[] fileData, String fileName, String contentType, Map<String, String> metadata);
    
    FileMetaData getFile(String fileId);
    
    Collection<FileMetaData> listFiles();
    
    boolean deleteFile(String fileId);
    
    boolean updateFile(String fileId, byte[] fileData, String contentType, Map<String, String> metadata);
}