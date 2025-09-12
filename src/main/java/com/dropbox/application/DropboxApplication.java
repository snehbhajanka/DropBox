package com.dropbox.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileDescriptor;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@SpringBootApplication
@RestController
public class DropboxApplication {

	private static final Map<String,FileMetaData> fileStorage=new HashMap<>();
	
	// Security constants
	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
	private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
		"image/jpeg", "image/png", "image/gif", "image/webp",
		"text/plain", "text/csv", "application/pdf",
		"application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
	);
	private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
	private static final int MAX_FILENAME_LENGTH = 255;

	public static void main(String[] args) {
		SpringApplication.run(DropboxApplication.class, args);
	}

	/**
	 * Validates and sanitizes file name to prevent path traversal attacks
	 */
	public String sanitizeFileName(String fileName) {
		if (fileName == null || fileName.trim().isEmpty()) {
			throw new IllegalArgumentException("File name cannot be null or empty");
		}
		
		String originalFileName = fileName.trim();
		
		// Check for path traversal attempts before any sanitization
		if (originalFileName.contains("..") || 
			originalFileName.contains("/") || 
			originalFileName.contains("\\") ||
			originalFileName.contains("\0")) {
			throw new IllegalArgumentException("File name contains path traversal sequences or invalid path characters");
		}
		
		// Check for valid characters only
		if (!SAFE_FILENAME_PATTERN.matcher(originalFileName).matches()) {
			throw new IllegalArgumentException("File name contains invalid characters. Only alphanumeric characters, dots, underscores, and hyphens are allowed.");
		}
		
		// Check length
		if (originalFileName.length() > MAX_FILENAME_LENGTH) {
			throw new IllegalArgumentException("File name is too long. Maximum length is " + MAX_FILENAME_LENGTH + " characters.");
		}
		
		// Prevent reserved names
		Set<String> reservedNames = Set.of("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", 
			"COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");
		if (reservedNames.contains(originalFileName.toUpperCase())) {
			throw new IllegalArgumentException("File name uses a reserved system name");
		}
		
		return originalFileName;
	}
	
	/**
	 * Validates file content and metadata for security
	 */
	public void validateFile(MultipartFile file, String fileName) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("File cannot be null or empty");
		}
		
		// Check file size
		if (file.getSize() > MAX_FILE_SIZE) {
			throw new IllegalArgumentException("File size exceeds maximum allowed size of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
		}
		
		// Check file type
		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_FILE_TYPES.contains(contentType)) {
			throw new IllegalArgumentException("File type not allowed. Allowed types: " + ALLOWED_FILE_TYPES);
		}
		
		// Additional validation: check file extension matches content type
		String fileExtension = getFileExtension(fileName);
		if (!isValidFileExtension(fileExtension, contentType)) {
			throw new IllegalArgumentException("File extension does not match content type");
		}
	}
	
	private String getFileExtension(String fileName) {
		int lastDotIndex = fileName.lastIndexOf('.');
		return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1).toLowerCase() : "";
	}
	
	private boolean isValidFileExtension(String extension, String contentType) {
		Map<String, Set<String>> validExtensions = Map.of(
			"image/jpeg", Set.of("jpg", "jpeg"),
			"image/png", Set.of("png"),
			"image/gif", Set.of("gif"),
			"image/webp", Set.of("webp"),
			"text/plain", Set.of("txt"),
			"text/csv", Set.of("csv"),
			"application/pdf", Set.of("pdf"),
			"application/msword", Set.of("doc"),
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx")
		);
		
		Set<String> allowedExtensions = validExtensions.get(contentType);
		return allowedExtensions != null && allowedExtensions.contains(extension);
	}
	
	/**
	 * Validates metadata to prevent injection attacks
	 */
	public void validateMetadata(Map<String, String> metadata) {
		if (metadata == null) {
			return;
		}
		
		// Limit number of metadata entries
		if (metadata.size() > 10) {
			throw new IllegalArgumentException("Too many metadata entries. Maximum 10 allowed.");
		}
		
		for (Map.Entry<String, String> entry : metadata.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			
			// Validate key
			if (key == null || key.trim().isEmpty()) {
				throw new IllegalArgumentException("Metadata key cannot be null or empty");
			}
			if (key.length() > 50) {
				throw new IllegalArgumentException("Metadata key too long. Maximum 50 characters allowed.");
			}
			if (!key.matches("^[a-zA-Z0-9_-]+$")) {
				throw new IllegalArgumentException("Metadata key contains invalid characters. Only alphanumeric, underscore, and hyphen allowed.");
			}
			
			// Validate value
			if (value != null && value.length() > 500) {
				throw new IllegalArgumentException("Metadata value too long. Maximum 500 characters allowed.");
			}
			
			// Check for potential script injection
			if (value != null && (value.contains("<script") || value.contains("javascript:") || value.contains("on"))) {
				throw new IllegalArgumentException("Metadata value contains potentially malicious content");
			}
		}
	}


@GetMapping("/files")
public ResponseEntity<Map<String, Object>> listFiles(){
	if(fileStorage!=null && fileStorage.values()!=null && !fileStorage.values().isEmpty())
		return ResponseEntity.ok(Map.of("files",fileStorage.values()));
	else return ResponseEntity.ok(Map.of("status","No files to display"));
}

@GetMapping("/files/{fileID}")
public ResponseEntity<byte[]> readFile(@PathVariable String fileID){
	FileMetaData file=fileStorage.get(fileID);
	if(file!=null){
		return ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=" + file.getFileName())
				.body(file.getData());
	} else{
		return ResponseEntity.notFound().build();
	}
}

@PostMapping("/files/upload")
public ResponseEntity<Map<String,String>> uploadFile(
		@RequestParam("file") MultipartFile file,
		@RequestParam("file_name") String fileName,
		@RequestParam(value = "metadata",required = false) Map<String,String> metaData){
	try {
		// Sanitize and validate file name
		String sanitizedFileName = sanitizeFileName(fileName);
		
		// Validate file content and metadata
		validateFile(file, sanitizedFileName);
		
		// Validate metadata if provided
		if (metaData != null) {
			validateMetadata(metaData);
		}
		
		String fileID = UUID.randomUUID().toString();
		byte[] fileData = file.getBytes();
		FileMetaData fileMetaData = new FileMetaData(fileID,
				sanitizedFileName, LocalDateTime.now(), file.getSize(), file.getContentType(), metaData, fileData);
		fileStorage.put(fileID, fileMetaData);
		return ResponseEntity.ok(Map.of("file_id",fileID, "message", "File uploaded successfully"));
	} catch (IllegalArgumentException e) {
		return ResponseEntity.badRequest()
				.body(Map.of("error", e.getMessage()));
	} catch(Exception e){
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error","Failed to upload the file"));
	}
}

@DeleteMapping("/files/{fileID}")
public ResponseEntity<?> deleteFile(@PathVariable String fileID){
		if(fileStorage.containsKey(fileID)){
			fileStorage.remove(fileID);
			return ResponseEntity.ok(Map.of("message","File deleted successfully"));
		} else {
			return  ResponseEntity.notFound().build();
		}
}

@PutMapping("/files/{fileID}")
public ResponseEntity<?> updateFile(@PathVariable String fileID,
									@RequestParam(value="file",required = false) MultipartFile file,
									@RequestParam(value ="metadata",required = false) Map<String,String> metaData){
		try {
			FileMetaData fileMetaData = fileStorage.get(fileID);
			if (fileMetaData == null) {
				return ResponseEntity.notFound().build();
			}
			
			// Validate new file if provided
			if (file != null) {
				validateFile(file, fileMetaData.getFileName());
				fileMetaData.setData(file.getBytes());
				fileMetaData.setSize(file.getSize());
				fileMetaData.setContentType(file.getContentType());
			}
			
			// Validate and update metadata if provided
			if (metaData != null) {
				validateMetadata(metaData);
				if (fileMetaData.getMetadata() == null) {
					fileMetaData.setMetadata(new HashMap<>());
				}
				fileMetaData.getMetadata().putAll(metaData);
			}
			
			return ResponseEntity.ok(Map.of(
				"message", "File updated successfully",
				"metadata", fileMetaData.getMetadata() != null ? fileMetaData.getMetadata() : Map.of()
			));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", e.getMessage()));
		} catch (Exception e){
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error","Failed to update the file"));
		}
}


}
