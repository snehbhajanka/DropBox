package com.dropbox.application.service;

import com.dropbox.application.FileMetaData;
import com.dropbox.application.config.AwsProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {
    
    @Autowired
    private S3Client s3Client;
    
    @Autowired
    private AwsProperties awsProperties;
    
    @Override
    public String uploadFile(byte[] fileData, String fileName, String contentType, Map<String, String> metadata) {
        String fileId = UUID.randomUUID().toString();
        String key = fileId + "_" + fileName;
        
        // Prepare metadata for S3
        Map<String, String> s3Metadata = new HashMap<>();
        s3Metadata.put("original-name", fileName);
        s3Metadata.put("file-id", fileId);
        if (metadata != null) {
            s3Metadata.putAll(metadata);
        }
        
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .metadata(s3Metadata)
                    .build();
                    
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileData));
            return fileId;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }
    
    @Override
    public FileMetaData getFile(String fileId) {
        try {
            // List objects to find the file with the given fileId
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .prefix(fileId + "_")
                    .build();
                    
            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
            
            if (listResponse.contents().isEmpty()) {
                return null;
            }
            
            S3Object s3Object = listResponse.contents().get(0);
            String key = s3Object.key();
            
            // Get object metadata
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .key(key)
                    .build();
                    
            HeadObjectResponse headResponse = s3Client.headObject(headRequest);
            
            // Get object content
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .key(key)
                    .build();
                    
            byte[] fileData = s3Client.getObject(getRequest).readAllBytes();
            
            // Extract original filename from metadata or key
            String originalName = headResponse.metadata().getOrDefault("original-name", 
                    key.substring(key.indexOf("_") + 1));
            
            LocalDateTime createdAt = headResponse.lastModified().atOffset(ZoneOffset.UTC).toLocalDateTime();
            
            return new FileMetaData(fileId, originalName, createdAt, headResponse.contentLength(),
                    headResponse.contentType(), headResponse.metadata(), fileData);
                    
        } catch (Exception e) {
            throw new RuntimeException("Failed to get file from S3", e);
        }
    }
    
    @Override
    public Collection<FileMetaData> listFiles() {
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .build();
                    
            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
            
            return listResponse.contents().stream()
                    .map(this::convertToFileMetaData)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            throw new RuntimeException("Failed to list files from S3", e);
        }
    }
    
    @Override
    public boolean deleteFile(String fileId) {
        try {
            // Find the object key for the given fileId
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .prefix(fileId + "_")
                    .build();
                    
            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
            
            if (listResponse.contents().isEmpty()) {
                return false;
            }
            
            String key = listResponse.contents().get(0).key();
            
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .key(key)
                    .build();
                    
            s3Client.deleteObject(deleteRequest);
            return true;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }
    
    @Override
    public boolean updateFile(String fileId, byte[] fileData, String contentType, Map<String, String> metadata) {
        // For S3, we implement update by replacing the object
        FileMetaData existingFile = getFile(fileId);
        if (existingFile == null) {
            return false;
        }
        
        // Use existing data if new data is not provided
        byte[] dataToUpload = fileData != null ? fileData : existingFile.getData();
        String typeToUse = contentType != null ? contentType : existingFile.getContentType();
        
        // Merge metadata
        Map<String, String> mergedMetadata = new HashMap<>(existingFile.getMetadata());
        if (metadata != null) {
            mergedMetadata.putAll(metadata);
        }
        
        // Delete existing file and upload new one
        deleteFile(fileId);
        String newFileId = uploadFile(dataToUpload, existingFile.getFileName(), typeToUse, mergedMetadata);
        
        return newFileId != null;
    }
    
    private FileMetaData convertToFileMetaData(S3Object s3Object) {
        try {
            String key = s3Object.key();
            String fileId = key.substring(0, key.indexOf("_"));
            
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .key(key)
                    .build();
                    
            HeadObjectResponse headResponse = s3Client.headObject(headRequest);
            
            String originalName = headResponse.metadata().getOrDefault("original-name", 
                    key.substring(key.indexOf("_") + 1));
            
            LocalDateTime createdAt = s3Object.lastModified().atOffset(ZoneOffset.UTC).toLocalDateTime();
            
            // Return metadata without actual file content for listing (performance optimization)
            return new FileMetaData(fileId, originalName, createdAt, s3Object.size(),
                    headResponse.contentType(), headResponse.metadata(), null);
                    
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert S3 object to FileMetaData", e);
        }
    }
}