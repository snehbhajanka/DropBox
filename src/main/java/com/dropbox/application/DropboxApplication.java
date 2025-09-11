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
	
	// Maximum file size: 10MB
	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

	public static void main(String[] args) {

		SpringApplication.run(DropboxApplication.class, args);
	}
	
	/**
	 * Validates file name to prevent path traversal attacks
	 */
	private boolean isValidFileName(String fileName) {
		if (fileName == null || fileName.trim().isEmpty()) {
			return false;
		}
		
		// Remove any path separators and check for path traversal attempts
		String cleanFileName = fileName.trim();
		
		// Check for path traversal patterns
		if (cleanFileName.contains("..") || 
			cleanFileName.contains("/") || 
			cleanFileName.contains("\\") ||
			cleanFileName.contains(":") ||
			cleanFileName.startsWith(".")) {
			return false;
		}
		
		// Check for reasonable length
		if (cleanFileName.length() > 255) {
			return false;
		}
		
		return true;
	}


@GetMapping("/files")
public ResponseEntity<Map<String, Object>> listFiles(){
	if(fileStorage!=null && fileStorage.values()!=null && !fileStorage.values().isEmpty())
		return ResponseEntity.ok(Map.of("files",fileStorage.values()));
	else return ResponseEntity.ok(Map.of("status","No files to display"));
}

@GetMapping("/files/{fileID}")
public ResponseEntity<byte[]> readFile(@PathVariable String fileID){
	// Basic input validation
	if (fileID == null || fileID.trim().isEmpty()) {
		return ResponseEntity.badRequest().build();
	}
	
	FileMetaData file = fileStorage.get(fileID.trim());
	if (file != null && file.getData() != null) {
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
		// Validate file name for security
		if (!isValidFileName(fileName)) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Invalid file name"));
		}
		
		// Validate file size (10MB limit)
		if (file.getSize() > 10 * 1024 * 1024) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "File size exceeds 10MB limit"));
		}
		
		// Validate file is not empty
		if (file.isEmpty()) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "File cannot be empty"));
		}
		
		String fileID = UUID.randomUUID().toString();
		byte[] fileData = file.getBytes();
		FileMetaData fileMetaData = new FileMetaData(fileID,
				fileName, LocalDateTime.now(), file.getSize(), file.getContentType(), metaData, fileData);
		fileStorage.put(fileID, fileMetaData);
		return ResponseEntity.ok(Map.of("file_id",fileID));
	} catch(Exception e){
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error","Upload failed"));
	}
}

@DeleteMapping("/files/{fileID}")
public ResponseEntity<?> deleteFile(@PathVariable String fileID){
	// Basic input validation
	if (fileID == null || fileID.trim().isEmpty()) {
		return ResponseEntity.badRequest()
				.body(Map.of("error", "Invalid file ID"));
	}
	
	if(fileStorage.containsKey(fileID.trim())){
		fileStorage.remove(fileID.trim());
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
			FileMetaData fileMetaData = fileStorage.get(fileID);
			if (fileMetaData == null) {
				return ResponseEntity.notFound().build();
			}
			
			if (file != null) {
				// Validate file size if new file is provided
				if (file.getSize() > MAX_FILE_SIZE) {
					return ResponseEntity.badRequest()
							.body(Map.of("error", "File size exceeds 10MB limit"));
				}
				
				if (file.isEmpty()) {
					return ResponseEntity.badRequest()
							.body(Map.of("error", "File cannot be empty"));
				}
				
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
			
			return ResponseEntity.ok(Map.of("message", "File updated successfully"));
		} catch (Exception e){
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error","Update failed"));
		}
}


}
