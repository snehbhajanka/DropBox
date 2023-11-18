package com.dropbox.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
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




	/*Upload File API: Allow users to upload files onto the platform.
	Endpoint: POST /files/upload
	Input: File binary data, file name, metadata (if any)
	Output: A unique file identifier
	Metadata to Save: File name, createdAt timestamp, size, file type
2. Read File API: Retrieve a specific file based on a unique identifier.
			Endpoint: GET /files/{fileId}
	Input: Unique file identifier
	Output: File binary data
3. Update File API: Update an existing file or its metadata.
			Endpoint: PUT /files/{fileId}
	Input: New file binary data or new metadata
	Output: Updated metadata or a success message
4. Delete File API: Delete a specific file based on a unique identifier.
			Endpoint: DELETE /files/{fileId}

	Input: Unique file identifier
	Output: A success or failure message
5. List Files API: List all available files and their metadata.
			Endpoint: GET /files
	Input: None
	Output: A list of file metadata objects, including file IDs, names, createdAt timestamps, etc.*/


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


}
