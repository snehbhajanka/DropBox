package com.dropbox.application;

import com.dropbox.application.service.S3FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication
@RestController
public class DropboxApplication {

	@Autowired(required = false)
	private S3FileService s3FileService;

	// Fallback in-memory storage for testing or when S3 is not available
	private static final Map<String,FileMetaData> fileStorage = new HashMap<>();

	public static void main(String[] args) {
		SpringApplication.run(DropboxApplication.class, args);
	}


@GetMapping("/files")
public ResponseEntity<Map<String, Object>> listFiles(){
	try {
		if (s3FileService != null) {
			// Use S3 service when available
			List<FileMetaData> files = s3FileService.listFiles();
			if (files != null && !files.isEmpty()) {
				return ResponseEntity.ok(Map.of("files", files));
			} else {
				return ResponseEntity.ok(Map.of("status", "No files to display"));
			}
		} else {
			// Fallback to in-memory storage (for tests)
			if (fileStorage != null && !fileStorage.values().isEmpty()) {
				return ResponseEntity.ok(Map.of("files", fileStorage.values()));
			} else {
				return ResponseEntity.ok(Map.of("status", "No files to display"));
			}
		}
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to list files"));
	}
}

@GetMapping("/files/{fileID}")
public ResponseEntity<byte[]> readFile(@PathVariable String fileID){
	try {
		if (s3FileService != null) {
			// Use S3 service when available
			FileMetaData file = s3FileService.getFile(fileID);
			if (file != null) {
				return ResponseEntity.ok()
						.header("Content-Disposition", "attachment; filename=" + file.getFileName())
						.body(file.getData());
			} else {
				return ResponseEntity.notFound().build();
			}
		} else {
			// Fallback to in-memory storage (for tests)
			FileMetaData file = fileStorage.get(fileID);
			if (file != null) {
				return ResponseEntity.ok()
						.header("Content-Disposition", "attachment; filename=" + file.getFileName())
						.body(file.getData());
			} else {
				return ResponseEntity.notFound().build();
			}
		}
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
	}
}

@PostMapping("/files/upload")
public ResponseEntity<Map<String,String>> uploadFile(
		@RequestParam("file") MultipartFile file,
		@RequestParam("file_name") String fileName,
		@RequestParam(value = "metadata", required = false) Map<String,String> metaData){
	try {
		if (s3FileService != null) {
			// Use S3 service when available
			String fileID = s3FileService.uploadFile(file, fileName, metaData);
			return ResponseEntity.ok(Map.of("file_id", fileID));
		} else {
			// Fallback to in-memory storage (for tests)
			String fileID = UUID.randomUUID().toString();
			byte[] fileData = file.getBytes();
			FileMetaData fileMetaData = new FileMetaData(fileID,
					fileName, LocalDateTime.now(), file.getSize(), file.getContentType(), metaData, fileData);
			fileStorage.put(fileID, fileMetaData);
			return ResponseEntity.ok(Map.of("file_id", fileID));
		}
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to upload the file"));
	}
}

@DeleteMapping("/files/{fileID}")
public ResponseEntity<?> deleteFile(@PathVariable String fileID){
	try {
		if (s3FileService != null) {
			// Use S3 service when available
			boolean deleted = s3FileService.deleteFile(fileID);
			if (deleted) {
				return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
			} else {
				return ResponseEntity.notFound().build();
			}
		} else {
			// Fallback to in-memory storage (for tests)
			if (fileStorage.containsKey(fileID)) {
				fileStorage.remove(fileID);
				return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
			} else {
				return ResponseEntity.notFound().build();
			}
		}
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to delete the file"));
	}
}

@PutMapping("/files/{fileID}")
public ResponseEntity<?> updateFile(@PathVariable String fileID,
									@RequestParam(value="file", required = false) MultipartFile file,
									@RequestParam(value="metadata", required = false) Map<String,String> metaData){
	try {
		if (s3FileService != null) {
			// Use S3 service when available
			FileMetaData updatedFile = s3FileService.updateFile(fileID, file, metaData);
			if (updatedFile != null) {
				return ResponseEntity.ok(updatedFile.getMetadata());
			} else {
				return ResponseEntity.notFound().build();
			}
		} else {
			// Fallback to in-memory storage (for tests)
			FileMetaData fileMetaData = fileStorage.get(fileID);
			if (fileMetaData != null) {
				if (file != null) {
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
		}
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to update the file"));
	}
}


}
