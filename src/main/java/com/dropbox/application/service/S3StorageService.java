package com.dropbox.application.service;

import com.dropbox.application.FileMetaData;
import com.dropbox.application.config.S3Configuration;
import com.dropbox.application.config.S3Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * S3-based implementation of FileStorageService with secure configuration.
 * This service ensures that all S3 operations are performed with public write access blocked.
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageService implements FileStorageService {

    private static final Logger LOGGER = Logger.getLogger(S3StorageService.class.getName());
    private static final String METADATA_SUFFIX = ".metadata";

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Properties s3Properties;

    @Autowired
    private S3Configuration s3Configuration;

    @PostConstruct
    public void initialize() {
        // Ensure bucket exists and is securely configured
        s3Configuration.createSecureBucket(s3Client, s3Properties.getBucketName());
        LOGGER.info("S3StorageService initialized with secure bucket: " + s3Properties.getBucketName());
    }

    @Override
    public void storeFile(String fileId, FileMetaData fileMetaData) {
        try {
            // Store the actual file data
            PutObjectRequest fileRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(fileId)
                    .contentType(fileMetaData.getContentType())
                    .serverSideEncryption(s3Properties.isEncryptionEnabled() ? 
                            ServerSideEncryption.AES256 : null)
                    .build();

            s3Client.putObject(fileRequest, RequestBody.fromBytes(fileMetaData.getData()));

            // Store metadata separately
            byte[] metadataBytes = serializeMetadata(fileMetaData);
            PutObjectRequest metadataRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(fileId + METADATA_SUFFIX)
                    .contentType("application/octet-stream")
                    .serverSideEncryption(s3Properties.isEncryptionEnabled() ? 
                            ServerSideEncryption.AES256 : null)
                    .build();

            s3Client.putObject(metadataRequest, RequestBody.fromBytes(metadataBytes));

            LOGGER.info("Successfully stored file with ID: " + fileId + " in secure S3 bucket");
        } catch (Exception e) {
            LOGGER.severe("Failed to store file in S3: " + e.getMessage());
            throw new RuntimeException("Failed to store file in S3", e);
        }
    }

    @Override
    public FileMetaData getFile(String fileId) {
        try {
            // First, get the metadata
            GetObjectRequest metadataRequest = GetObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(fileId + METADATA_SUFFIX)
                    .build();

            byte[] metadataBytes = s3Client.getObject(metadataRequest).readAllBytes();
            FileMetaData metadata = deserializeMetadata(metadataBytes);

            // Then get the file data
            GetObjectRequest fileRequest = GetObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(fileId)
                    .build();

            byte[] fileData = s3Client.getObject(fileRequest).readAllBytes();
            metadata.setData(fileData);

            return metadata;
        } catch (NoSuchKeyException e) {
            return null; // File not found
        } catch (Exception e) {
            LOGGER.severe("Failed to retrieve file from S3: " + e.getMessage());
            throw new RuntimeException("Failed to retrieve file from S3", e);
        }
    }

    @Override
    public boolean deleteFile(String fileId) {
        try {
            // Delete the file
            DeleteObjectRequest fileDeleteRequest = DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(fileId)
                    .build();
            s3Client.deleteObject(fileDeleteRequest);

            // Delete the metadata
            DeleteObjectRequest metadataDeleteRequest = DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(fileId + METADATA_SUFFIX)
                    .build();
            s3Client.deleteObject(metadataDeleteRequest);

            LOGGER.info("Successfully deleted file with ID: " + fileId);
            return true;
        } catch (Exception e) {
            LOGGER.severe("Failed to delete file from S3: " + e.getMessage());
            return false;
        }
    }

    @Override
    public Collection<FileMetaData> listFiles() {
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(s3Properties.getBucketName())
                    .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
            Collection<FileMetaData> files = new ArrayList<>();

            for (S3Object s3Object : listResponse.contents()) {
                String key = s3Object.key();
                // Skip metadata files and only process actual file objects
                if (!key.endsWith(METADATA_SUFFIX)) {
                    try {
                        FileMetaData fileMetaData = getFile(key);
                        if (fileMetaData != null) {
                            files.add(fileMetaData);
                        }
                    } catch (Exception e) {
                        LOGGER.warning("Failed to load metadata for file: " + key);
                    }
                }
            }

            return files;
        } catch (Exception e) {
            LOGGER.severe("Failed to list files from S3: " + e.getMessage());
            throw new RuntimeException("Failed to list files from S3", e);
        }
    }

    @Override
    public boolean fileExists(String fileId) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(fileId)
                    .build();
            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            LOGGER.severe("Failed to check file existence in S3: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void updateFile(String fileId, FileMetaData fileMetaData) {
        if (fileExists(fileId)) {
            storeFile(fileId, fileMetaData);
        }
    }

    private byte[] serializeMetadata(FileMetaData metadata) throws IOException {
        // Create a copy without the data to avoid storing large data twice
        FileMetaData metadataOnly = new FileMetaData(
                metadata.getFileID(),
                metadata.getFileName(),
                metadata.getCreatedAt(),
                metadata.getSize(),
                metadata.getContentType(),
                metadata.getMetadata(),
                null // Don't include data in metadata
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(metadataOnly);
        }
        return baos.toByteArray();
    }

    private FileMetaData deserializeMetadata(byte[] data) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new java.io.ByteArrayInputStream(data))) {
            return (FileMetaData) ois.readObject();
        }
    }
}