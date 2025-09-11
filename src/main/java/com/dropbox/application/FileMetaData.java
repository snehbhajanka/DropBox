package com.dropbox.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class FileMetaData {

    private String fileID;
    private String accountId;



    private String contentType;

    public FileMetaData(String fileID, String accountId, String fileName, LocalDateTime createdAt, long size, String ContentType, Map<String, String> metadata,byte[] data) {
        this.fileID = fileID;
        this.accountId = accountId;
        this.fileName = fileName;
        this.createdAt = createdAt;
        this.contentType=ContentType;
        this.size = size;
        this.metadata = metadata;
        this.data=data;
    }

    private String fileName;
    private LocalDateTime createdAt;
    private long size;

    private byte[] data;

    private Map<String,String> metadata;

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public String getFileID() {
        return fileID;
    }

    public void setFileID(String fileID) {
        this.fileID = fileID;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
