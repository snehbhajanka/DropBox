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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;

@SpringBootApplication
@RestController
public class DropboxApplication {

	private static final Map<String,FileMetaData> fileStorage=new HashMap<>();
	
	// Security constants
	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB max file size
	private static final List<String> ALLOWED_FILE_TYPES = Arrays.asList(
		"text/plain", "text/csv", "application/pdf", "image/jpeg", "image/png", 
		"application/json", "application/xml", "application/zip"
	);

	public static void main(String[] args) {

		SpringApplication.run(DropboxApplication.class, args);
	}
	
	// Security helper methods
	private String sanitizeFilename(String filename) {
		if (filename == null || filename.trim().isEmpty()) {
			return "unknown_file";
		}
		// Remove or replace characters that could be used for header injection
		return filename.replaceAll("[\\r\\n\\t\\f\\x08\"\\\\]", "_")
				.replaceAll("[^a-zA-Z0-9._-]", "_")
				.substring(0, Math.min(filename.length(), 100)); // Limit length
	}
	
	private boolean isValidFileType(String contentType) {
		return contentType != null && ALLOWED_FILE_TYPES.contains(contentType);
	}
	
	private boolean isValidFileSize(long size) {
		return size > 0 && size <= MAX_FILE_SIZE;
	}
	
	private String validateFileName(String fileName) {
		if (fileName == null || fileName.trim().isEmpty()) {
			throw new IllegalArgumentException("File name cannot be empty");
		}
		if (fileName.length() > 255) {
			throw new IllegalArgumentException("File name too long");
		}
		if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
			throw new IllegalArgumentException("Invalid characters in file name");
		}
		return fileName.trim();
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
		// Sanitize filename to prevent header injection attacks
		String sanitizedFilename = sanitizeFilename(file.getFileName());
		return ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=\"" + sanitizedFilename + "\"")
				.body(file.getData());
	} else{
		return ResponseEntity.notFound().build();
	}
}

@PostMapping("/files/upload")
public ResponseEntity<Map<String,String>> uploadFile(
		@RequestParam("file") MultipartFile file,
		@RequestParam("file_name") String fileNname,
		@RequestParam(value = "metadata",required = false) Map<String,String> metaData){
	try {
		// Validate file size
		if (!isValidFileSize(file.getSize())) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "File size exceeds maximum allowed size of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB"));
		}
		
		// Validate file type
		if (!isValidFileType(file.getContentType())) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "File type not allowed. Allowed types: " + String.join(", ", ALLOWED_FILE_TYPES)));
		}
		
		// Validate file name
		String validatedFileName = validateFileName(fileNname);
		
		String fileID = UUID.randomUUID().toString();
		byte[] fileData = file.getBytes();
		FileMetaData fileMetaData = new FileMetaData(fileID,
				validatedFileName, LocalDateTime.now(), file.getSize(), file.getContentType(), metaData, fileData);
		fileStorage.put(fileID, fileMetaData);
		return ResponseEntity.ok(Map.of("file_id",fileID));
	} catch(IllegalArgumentException e) {
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
			if (fileMetaData != null) {
				if (file != null) {
					// Validate file size
					if (!isValidFileSize(file.getSize())) {
						return ResponseEntity.badRequest()
								.body(Map.of("error", "File size exceeds maximum allowed size of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB"));
					}
					
					// Validate file type
					if (!isValidFileType(file.getContentType())) {
						return ResponseEntity.badRequest()
								.body(Map.of("error", "File type not allowed. Allowed types: " + String.join(", ", ALLOWED_FILE_TYPES)));
					}
					
					fileMetaData.setData(file.getBytes());
					fileMetaData.setSize(file.getSize());
					fileMetaData.setContentType(file.getContentType());
				}
				if (metaData != null) {
					fileMetaData.getMetadata().putAll(metaData);
				}
				return ResponseEntity.ok(fileMetaData.getMetadata());
			} else {
				return ResponseEntity.notFound().build();
			}
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", e.getMessage()));
		} catch (Exception e){
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error","Failed to update the file"));

		}
}


}
