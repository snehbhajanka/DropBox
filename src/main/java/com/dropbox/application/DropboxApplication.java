package com.dropbox.application;

import com.dropbox.application.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@SpringBootApplication
@RestController
public class DropboxApplication {

	@Autowired
	private S3Service s3Service;

	public static void main(String[] args) {
		SpringApplication.run(DropboxApplication.class, args);
	}


@GetMapping("/files")
public ResponseEntity<Map<String, Object>> listFiles(){
	try {
		List<FileMetaData> files = s3Service.listFiles();
		if (files != null && !files.isEmpty()) {
			return ResponseEntity.ok(Map.of("files", files));
		} else {
			return ResponseEntity.ok(Map.of("status", "No files to display"));
		}
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to list files"));
	}
}

@GetMapping("/files/{fileID}")
public ResponseEntity<byte[]> readFile(@PathVariable String fileID){
	try {
		FileMetaData fileMetadata = s3Service.getFileMetadata(fileID);
		if (fileMetadata != null) {
			byte[] fileData = s3Service.getFileData(fileID);
			if (fileData != null) {
				return ResponseEntity.ok()
						.header("Content-Disposition", "attachment; filename=" + fileMetadata.getFileName())
						.body(fileData);
			}
		}
		return ResponseEntity.notFound().build();
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
		byte[] fileData = file.getBytes();
		String fileID = s3Service.uploadFile(fileName, fileData, file.getContentType(), metaData);
		return ResponseEntity.ok(Map.of("file_id", fileID));
	} catch(Exception e){
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to upload the file"));
	}
}

@DeleteMapping("/files/{fileID}")
public ResponseEntity<?> deleteFile(@PathVariable String fileID){
	try {
		if (s3Service.getFileMetadata(fileID) != null) {
			boolean deleted = s3Service.deleteFile(fileID);
			if (deleted) {
				return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
			} else {
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.body(Map.of("error", "Failed to delete file"));
			}
		} else {
			return ResponseEntity.notFound().build();
		}
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to delete file"));
	}
}

@PutMapping("/files/{fileID}")
public ResponseEntity<?> updateFile(@PathVariable String fileID,
									@RequestParam(value="file", required = false) MultipartFile file,
									@RequestParam(value ="metadata", required = false) Map<String,String> metaData){
	try {
		FileMetaData existingFile = s3Service.getFileMetadata(fileID);
		if (existingFile != null) {
			byte[] newData = null;
			String contentType = null;
			
			if (file != null) {
				newData = file.getBytes();
				contentType = file.getContentType();
			} else {
				// If no new file provided, get existing data
				newData = s3Service.getFileData(fileID);
				contentType = existingFile.getContentType();
			}
			
			boolean updated = s3Service.updateFile(fileID, newData, contentType, metaData);
			if (updated) {
				FileMetaData updatedFile = s3Service.getFileMetadata(fileID);
				return ResponseEntity.ok(updatedFile.getMetadata());
			} else {
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.body(Map.of("error", "Failed to update file"));
			}
		} else {
			return ResponseEntity.notFound().build();
		}
	} catch (Exception e){
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to update the file"));
	}
}


}
