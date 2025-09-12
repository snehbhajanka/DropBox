package com.dropbox.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileDescriptor;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@SpringBootApplication
@RestController
public class DropboxApplication {

	private static final Map<String,FileMetaData> fileStorage=new HashMap<>();
	
	// Security configuration
	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
		"txt", "pdf", "doc", "docx", "jpg", "jpeg", "png", "gif", "csv", "json"
	);
	private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
		"exe", "bat", "sh", "cmd", "scr", "com", "pif", "vbs", "js", "jar", "war"
	);
	private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
	private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(".*(\\.\\.[\\/\\\\]|[\\/\\\\]\\.\\.|\\.\\.).*");

	public static void main(String[] args) {
		SpringApplication.run(DropboxApplication.class, args);
	}

	/**
	 * Validates file name for security issues
	 */
	private String validateAndSanitizeFileName(String fileName) {
		if (fileName == null || fileName.trim().isEmpty()) {
			throw new IllegalArgumentException("File name cannot be empty");
		}
		
		fileName = fileName.trim();
		
		// Check for path traversal attempts
		if (PATH_TRAVERSAL_PATTERN.matcher(fileName).matches()) {
			throw new IllegalArgumentException("File name contains invalid characters");
		}
		
		// Check for dangerous characters
		if (!SAFE_FILENAME_PATTERN.matcher(fileName).matches()) {
			throw new IllegalArgumentException("File name contains invalid characters");
		}
		
		// Check file extension
		String extension = getFileExtension(fileName).toLowerCase();
		if (BLOCKED_EXTENSIONS.contains(extension)) {
			throw new IllegalArgumentException("File type not allowed");
		}
		
		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new IllegalArgumentException("File type not supported");
		}
		
		return fileName;
	}
	
	/**
	 * Extracts file extension from filename
	 */
	private String getFileExtension(String fileName) {
		int lastDotIndex = fileName.lastIndexOf('.');
		if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
			return "";
		}
		return fileName.substring(lastDotIndex + 1);
	}
	
	/**
	 * Validates file size
	 */
	private void validateFileSize(long size) {
		if (size > MAX_FILE_SIZE) {
			throw new IllegalArgumentException("File size exceeds maximum allowed size");
		}
	}


@GetMapping("/files")
public ResponseEntity<Map<String, Object>> listFiles(){
	try {
		if(fileStorage != null && !fileStorage.isEmpty()) {
			// Return only metadata, not the actual file data for security
			List<Map<String, Object>> fileList = new ArrayList<>();
			for (FileMetaData file : fileStorage.values()) {
				Map<String, Object> fileInfo = new HashMap<>();
				fileInfo.put("fileID", file.getFileID());
				fileInfo.put("fileName", file.getFileName());
				fileInfo.put("size", file.getSize());
				fileInfo.put("contentType", file.getContentType());
				fileInfo.put("createdAt", file.getCreatedAt().toString());
				fileList.add(fileInfo);
			}
			return ResponseEntity.ok(Map.of("files", fileList));
		} else {
			return ResponseEntity.ok(Map.of("files", Collections.emptyList()));
		}
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to retrieve file list"));
	}
}

@GetMapping("/files/{fileID}")
public ResponseEntity<byte[]> readFile(@PathVariable String fileID){
	// Validate file ID format (basic UUID validation)
	if (fileID == null || fileID.trim().isEmpty()) {
		return ResponseEntity.badRequest().build();
	}
	
	try {
		UUID.fromString(fileID); // Validate it's a proper UUID
	} catch (IllegalArgumentException e) {
		return ResponseEntity.badRequest().build();
	}
	
	FileMetaData file = fileStorage.get(fileID);
	if(file != null){
		return ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=\"" + file.getFileName() + "\"")
				.header("Content-Type", file.getContentType())
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
		// Validate inputs
		if (file == null || file.isEmpty()) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "File is required"));
		}
		
		// Validate file size
		validateFileSize(file.getSize());
		
		// Validate and sanitize file name
		String sanitizedFileName = validateAndSanitizeFileName(fileName);
		
		// Generate secure file ID
		String fileID = UUID.randomUUID().toString();
		byte[] fileData = file.getBytes();
		
		FileMetaData fileMetaData = new FileMetaData(fileID,
				sanitizedFileName, LocalDateTime.now(), file.getSize(), file.getContentType(), metaData, fileData);
		fileStorage.put(fileID, fileMetaData);
		
		return ResponseEntity.ok(Map.of("file_id",fileID));
	} catch (IllegalArgumentException e){
		return ResponseEntity.badRequest()
				.body(Map.of("error", e.getMessage()));
	} catch(Exception e){
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error","Failed to upload the file"));
	}
}

@DeleteMapping("/files/{fileID}")
public ResponseEntity<?> deleteFile(@PathVariable String fileID){
	// Validate file ID format
	if (fileID == null || fileID.trim().isEmpty()) {
		return ResponseEntity.badRequest().build();
	}
	
	try {
		UUID.fromString(fileID); // Validate it's a proper UUID
	} catch (IllegalArgumentException e) {
		return ResponseEntity.badRequest().build();
	}
	
	if(fileStorage.containsKey(fileID)){
		fileStorage.remove(fileID);
		return ResponseEntity.ok(Map.of("message","File deleted successfully"));
	} else {
		return ResponseEntity.notFound().build();
	}
}

@PutMapping("/files/{fileID}")
public ResponseEntity<?> updateFile(@PathVariable String fileID,
									@RequestParam(value="file",required = false) MultipartFile file,
									@RequestParam(value ="metadata",required = false) Map<String,String> metaData){
	try {
		// Validate file ID format
		if (fileID == null || fileID.trim().isEmpty()) {
			return ResponseEntity.badRequest().build();
		}
		
		try {
			UUID.fromString(fileID); // Validate it's a proper UUID
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().build();
		}
		
		FileMetaData fileMetaData = fileStorage.get(fileID);
		if (fileMetaData != null) {
			if (file != null && !file.isEmpty()) {
				// Validate file size for updates
				validateFileSize(file.getSize());
				
				fileMetaData.setData(file.getBytes());
				fileMetaData.setSize(file.getSize());
				fileMetaData.setContentType(file.getContentType());
			}
			if (metaData != null) {
				if (fileMetaData.getMetadata() == null) {
					fileMetaData.setMetadata(new HashMap<>());
				}
				fileMetaData.getMetadata().putAll(metaData);
			}
			return ResponseEntity.ok(fileMetaData.getMetadata());
		} else {
			return ResponseEntity.notFound().build();
		}
	} catch (IllegalArgumentException e){
		return ResponseEntity.badRequest()
				.body(Map.of("error", e.getMessage()));
	} catch (Exception e){
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error","Failed to update the file"));
	}
}


}
