package com.dropbox.application.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.dropbox.application.FileMetaData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Profile("!test")
public class S3FileService {

    @Autowired
    private AmazonS3 amazonS3;

    @Value("${aws.s3.bucket.name:dropbox-secure-bucket}")
    private String bucketName;

    public List<FileMetaData> listFiles() {
        try {
            ListObjectsV2Request request = new ListObjectsV2Request()
                    .withBucketName(bucketName);
            
            ListObjectsV2Result result = amazonS3.listObjectsV2(request);
            
            return result.getObjectSummaries().stream()
                    .map(this::convertToFileMetaData)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to list files from S3", e);
        }
    }

    public FileMetaData getFile(String fileId) {
        try {
            S3Object s3Object = amazonS3.getObject(bucketName, fileId);
            ObjectMetadata metadata = s3Object.getObjectMetadata();
            
            // Read the object content
            byte[] content = s3Object.getObjectContent().readAllBytes();
            
            // Extract metadata
            Map<String, String> userMetadata = metadata.getUserMetadata();
            String fileName = userMetadata.get("filename");
            String contentType = metadata.getContentType();
            Date lastModified = metadata.getLastModified();
            long size = metadata.getContentLength();
            
            return new FileMetaData(
                fileId,
                fileName,
                convertToLocalDateTime(lastModified),
                size,
                contentType,
                userMetadata,
                content
            );
        } catch (Exception e) {
            return null; // File not found
        }
    }

    public String uploadFile(MultipartFile file, String fileName, Map<String, String> metadata) {
        try {
            String fileId = UUID.randomUUID().toString();
            byte[] fileContent = file.getBytes();
            
            // Prepare metadata
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(fileContent.length);
            objectMetadata.setContentType(file.getContentType());
            
            // Add custom metadata
            if (metadata != null) {
                objectMetadata.setUserMetadata(metadata);
            }
            objectMetadata.addUserMetadata("filename", fileName);
            objectMetadata.addUserMetadata("upload-time", LocalDateTime.now().toString());
            
            // Security: Ensure server-side encryption
            objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
            
            // Upload file to S3
            PutObjectRequest putRequest = new PutObjectRequest(
                bucketName,
                fileId,
                new ByteArrayInputStream(fileContent),
                objectMetadata
            );
            
            // Additional security: Set private ACL
            putRequest.setCannedAcl(CannedAccessControlList.Private);
            
            amazonS3.putObject(putRequest);
            
            return fileId;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    public boolean deleteFile(String fileId) {
        try {
            // Check if file exists
            if (!amazonS3.doesObjectExist(bucketName, fileId)) {
                return false;
            }
            
            amazonS3.deleteObject(bucketName, fileId);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }

    public FileMetaData updateFile(String fileId, MultipartFile file, Map<String, String> metadata) {
        try {
            // Get existing file metadata
            FileMetaData existingFile = getFile(fileId);
            if (existingFile == null) {
                return null;
            }
            
            ObjectMetadata objectMetadata = new ObjectMetadata();
            byte[] content;
            
            if (file != null) {
                // Update with new file content
                content = file.getBytes();
                objectMetadata.setContentLength(content.length);
                objectMetadata.setContentType(file.getContentType());
            } else {
                // Keep existing content
                content = existingFile.getData();
                objectMetadata.setContentLength(content.length);
                objectMetadata.setContentType(existingFile.getContentType());
            }
            
            // Merge metadata
            Map<String, String> updatedMetadata = new HashMap<>(existingFile.getMetadata());
            if (metadata != null) {
                updatedMetadata.putAll(metadata);
            }
            objectMetadata.setUserMetadata(updatedMetadata);
            objectMetadata.addUserMetadata("last-modified", LocalDateTime.now().toString());
            
            // Security: Ensure server-side encryption
            objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
            
            // Update file in S3
            PutObjectRequest putRequest = new PutObjectRequest(
                bucketName,
                fileId,
                new ByteArrayInputStream(content),
                objectMetadata
            );
            
            // Additional security: Set private ACL
            putRequest.setCannedAcl(CannedAccessControlList.Private);
            
            amazonS3.putObject(putRequest);
            
            // Return updated file metadata
            return getFile(fileId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to update file in S3", e);
        }
    }

    private FileMetaData convertToFileMetaData(S3ObjectSummary summary) {
        try {
            // Get object metadata
            ObjectMetadata metadata = amazonS3.getObjectMetadata(bucketName, summary.getKey());
            Map<String, String> userMetadata = metadata.getUserMetadata();
            
            String fileName = userMetadata.get("filename");
            if (fileName == null) {
                fileName = summary.getKey();
            }
            
            return new FileMetaData(
                summary.getKey(),
                fileName,
                convertToLocalDateTime(summary.getLastModified()),
                summary.getSize(),
                metadata.getContentType(),
                userMetadata,
                null // Don't load content for listing
            );
        } catch (Exception e) {
            // Return basic metadata if detailed metadata fetch fails
            return new FileMetaData(
                summary.getKey(),
                summary.getKey(),
                convertToLocalDateTime(summary.getLastModified()),
                summary.getSize(),
                "application/octet-stream",
                new HashMap<>(),
                null
            );
        }
    }

    private LocalDateTime convertToLocalDateTime(Date date) {
        if (date == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(date.toInstant(), TimeZone.getDefault().toZoneId());
    }
}