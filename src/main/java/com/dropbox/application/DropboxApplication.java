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
	// Validate fileID parameter
	FileSecurityValidator.ValidationResult fileIdValidation = 
		FileSecurityValidator.validateStringInput(fileID, "file_id", 255);
	if (!fileIdValidation.isValid()) {
		return ResponseEntity.badRequest().build();
	}
	
	FileMetaData file=fileStorage.get(fileID);
	if(file!=null){
		return ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=\"" + 
					FileSecurityValidator.sanitizeFilename(file.getFileName()) + "\"")
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
		// Validate file security
		FileSecurityValidator.ValidationResult fileValidation = FileSecurityValidator.validateFile(file);
		if (!fileValidation.isValid()) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", fileValidation.getErrorMessage()));
		}
		
		// Validate filename parameter
		FileSecurityValidator.ValidationResult fileNameValidation = 
			FileSecurityValidator.validateStringInput(fileName, "file_name", 255);
		if (!fileNameValidation.isValid()) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", fileNameValidation.getErrorMessage()));
		}
		
		// Validate metadata if provided
		if (metaData != null) {
			for (Map.Entry<String, String> entry : metaData.entrySet()) {
				FileSecurityValidator.ValidationResult keyValidation = 
					FileSecurityValidator.validateStringInput(entry.getKey(), "metadata key", 50);
				FileSecurityValidator.ValidationResult valueValidation = 
					FileSecurityValidator.validateStringInput(entry.getValue(), "metadata value", 500);
				
				if (!keyValidation.isValid()) {
					return ResponseEntity.badRequest()
							.body(Map.of("error", keyValidation.getErrorMessage()));
				}
				if (!valueValidation.isValid()) {
					return ResponseEntity.badRequest()
							.body(Map.of("error", valueValidation.getErrorMessage()));
				}
			}
		}
		
		String fileID = UUID.randomUUID().toString();
		byte[] fileData = file.getBytes();
		
		// Use sanitized filename for storage
		String sanitizedFileName = FileSecurityValidator.sanitizeFilename(
			fileName != null ? fileName : file.getOriginalFilename());
		
		FileMetaData fileMetaData = new FileMetaData(fileID,
				sanitizedFileName, LocalDateTime.now(), file.getSize(), 
				file.getContentType(), metaData, fileData);
		fileStorage.put(fileID, fileMetaData);
		return ResponseEntity.ok(Map.of("file_id",fileID));
	} catch(Exception e){
		// Log error for debugging but don't expose details to client
		System.err.println("File upload error: " + e.getMessage());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error","Upload failed"));
	}
}

@DeleteMapping("/files/{fileID}")
public ResponseEntity<?> deleteFile(@PathVariable String fileID){
	// Validate fileID parameter
	FileSecurityValidator.ValidationResult fileIdValidation = 
		FileSecurityValidator.validateStringInput(fileID, "file_id", 255);
	if (!fileIdValidation.isValid()) {
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
			// Validate fileID parameter
			FileSecurityValidator.ValidationResult fileIdValidation = 
				FileSecurityValidator.validateStringInput(fileID, "file_id", 255);
			if (!fileIdValidation.isValid()) {
				return ResponseEntity.badRequest()
						.body(Map.of("error", fileIdValidation.getErrorMessage()));
			}
			
			FileMetaData fileMetaData = fileStorage.get(fileID);
			if (fileMetaData == null) {
				return ResponseEntity.notFound().build();
			}
			
			// Validate file if provided
			if (file != null) {
				FileSecurityValidator.ValidationResult fileValidation = FileSecurityValidator.validateFile(file);
				if (!fileValidation.isValid()) {
					return ResponseEntity.badRequest()
							.body(Map.of("error", fileValidation.getErrorMessage()));
				}
				
				fileMetaData.setData(file.getBytes());
				fileMetaData.setSize(file.getSize());
				fileMetaData.setContentType(file.getContentType());
			}
			
			// Validate metadata if provided
			if (metaData != null) {
				for (Map.Entry<String, String> entry : metaData.entrySet()) {
					FileSecurityValidator.ValidationResult keyValidation = 
						FileSecurityValidator.validateStringInput(entry.getKey(), "metadata key", 50);
					FileSecurityValidator.ValidationResult valueValidation = 
						FileSecurityValidator.validateStringInput(entry.getValue(), "metadata value", 500);
					
					if (!keyValidation.isValid()) {
						return ResponseEntity.badRequest()
								.body(Map.of("error", keyValidation.getErrorMessage()));
					}
					if (!valueValidation.isValid()) {
						return ResponseEntity.badRequest()
								.body(Map.of("error", valueValidation.getErrorMessage()));
					}
				}
				fileMetaData.getMetadata().putAll(metaData);
			}
			
			return ResponseEntity.ok(fileMetaData.getMetadata());
		} catch (Exception e){
			// Log error for debugging but don't expose details to client
			System.err.println("File update error: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error","Update failed"));
		}
}


}
