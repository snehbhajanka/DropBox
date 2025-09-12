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
import java.util.regex.Pattern;

@SpringBootApplication
@RestController
public class DropboxApplication {

	private static final Map<String,FileMetaData> fileStorage=new HashMap<>();
	
	// Security validation patterns
	private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
	private static final int MAX_FILENAME_LENGTH = 255;
	
	// Validate file name to prevent path traversal attacks
	private boolean isValidFileName(String fileName) {
		if (fileName == null || fileName.trim().isEmpty()) {
			return false;
		}
		
		// Check length
		if (fileName.length() > MAX_FILENAME_LENGTH) {
			return false;
		}
		
		// Check for path traversal patterns
		if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
			return false;
		}
		
		// Check against safe pattern
		return SAFE_FILENAME_PATTERN.matcher(fileName).matches();
	}
	
	// Validate file type (basic validation)
	private boolean isValidFileType(String contentType) {
		if (contentType == null) {
			return false;
		}
		
		// Allow common safe file types
		return contentType.startsWith("text/") || 
			   contentType.startsWith("image/") ||
			   contentType.equals("application/pdf") ||
			   contentType.equals("application/json") ||
			   contentType.equals("application/xml");
	}

	public static void main(String[] args) {

		SpringApplication.run(DropboxApplication.class, args);
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
		@RequestParam("file_name") String fileNname,
		@RequestParam(value = "metadata",required = false) Map<String,String> metaData){
	try {
		// Validate file name for security
		if (!isValidFileName(fileNname)) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Invalid file name. File name must be alphanumeric with dots, hyphens, or underscores only."));
		}
		
		// Validate file type
		if (!isValidFileType(file.getContentType())) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Invalid file type. Only text, image, PDF, JSON, and XML files are allowed."));
		}
		
		// Validate file size (10MB limit)
		if (file.getSize() > 10 * 1024 * 1024) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "File size exceeds 10MB limit."));
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
					// Validate file type for updates
					if (!isValidFileType(file.getContentType())) {
						return ResponseEntity.badRequest()
								.body(Map.of("error", "Invalid file type. Only text, image, PDF, JSON, and XML files are allowed."));
					}
					
					// Validate file size (10MB limit)
					if (file.getSize() > 10 * 1024 * 1024) {
						return ResponseEntity.badRequest()
								.body(Map.of("error", "File size exceeds 10MB limit."));
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
