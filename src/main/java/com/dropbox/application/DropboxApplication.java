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

@SpringBootApplication
@RestController
public class DropboxApplication {

	private static final Map<String,FileMetaData> fileStorage=new HashMap<>();
	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB limit

	public static void main(String[] args) {

		SpringApplication.run(DropboxApplication.class, args);
	}

	/**
	 * Sanitizes filename to prevent header injection attacks
	 */
	private String sanitizeFileName(String fileName) {
		if (fileName == null) return "unknown";
		// Remove any characters that could be used for header injection
		return fileName.replaceAll("[\\r\\n\\t\"]", "_").trim();
	}

	/**
	 * Validates file name for security
	 */
	private boolean isValidFileName(String fileName) {
		if (fileName == null || fileName.trim().isEmpty()) return false;
		// Prevent directory traversal
		if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) return false;
		// Prevent control characters
		if (fileName.matches(".*[\\x00-\\x1f\\x7f].*")) return false;
		return true;
	}

	/**
	 * Validates file ID format
	 */
	private boolean isValidFileID(String fileID) {
		if (fileID == null || fileID.trim().isEmpty()) return false;
		// UUID format validation
		return fileID.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
	}


@GetMapping("/files")
public ResponseEntity<Map<String, Object>> listFiles(){
	if(fileStorage!=null && fileStorage.values()!=null && !fileStorage.values().isEmpty())
		return ResponseEntity.ok(Map.of("files",fileStorage.values()));
	else return ResponseEntity.ok(Map.of("status","No files to display"));
}

@GetMapping("/files/{fileID}")
public ResponseEntity<byte[]> readFile(@PathVariable String fileID){
	// Validate fileID format
	if (!isValidFileID(fileID)) {
		return ResponseEntity.badRequest().build();
	}
	
	FileMetaData file=fileStorage.get(fileID);
	if(file!=null){
		// Sanitize filename to prevent header injection
		String sanitizedFileName = sanitizeFileName(file.getFileName());
		return ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=\"" + sanitizedFileName + "\"")
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
		if (file.getSize() > MAX_FILE_SIZE) {
			return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
					.body(Map.of("error", "File size exceeds maximum limit of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB"));
		}
		
		// Validate file name
		if (!isValidFileName(fileNname)) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Invalid file name"));
		}
		
		// Validate file is not empty
		if (file.isEmpty()) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "File cannot be empty"));
		}
		
		String fileID = UUID.randomUUID().toString();
		byte[] fileData = file.getBytes();
		FileMetaData fileMetaData = new FileMetaData(fileID,
				fileNname, LocalDateTime.now(), file.getSize(), file.getContentType(), metaData, fileData);
		fileStorage.put(fileID, fileMetaData);
		return ResponseEntity.ok(Map.of("file_id",fileID));
	} catch(Exception e){
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error","Failed to upload the file"));
	}
}

@DeleteMapping("/files/{fileID}")
public ResponseEntity<?> deleteFile(@PathVariable String fileID){
		// Validate fileID format
		if (!isValidFileID(fileID)) {
			return ResponseEntity.badRequest().build();
		}
		
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
			// Validate fileID format
			if (!isValidFileID(fileID)) {
				return ResponseEntity.badRequest().build();
			}
			
			FileMetaData fileMetaData = fileStorage.get(fileID);
			if (fileMetaData != null) {
				if (file != null) {
					// Validate file size
					if (file.getSize() > MAX_FILE_SIZE) {
						return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
								.body(Map.of("error", "File size exceeds maximum limit of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB"));
					}
					
					// Validate file is not empty
					if (file.isEmpty()) {
						return ResponseEntity.badRequest()
								.body(Map.of("error", "File cannot be empty"));
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
		} catch (Exception e){
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error","Failed to update the file"));

		}
}


}
