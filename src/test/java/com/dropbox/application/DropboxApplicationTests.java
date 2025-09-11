package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DropboxApplicationTests {

	@Test
	void contextLoads() {
	}
	
	@Test
	void testUploadWithInvalidFileType() {
		DropboxApplication app = new DropboxApplication();
		
		MockMultipartFile file = new MockMultipartFile(
			"file", 
			"malicious.exe", 
			"application/octet-stream", 
			"malicious content".getBytes()
		);
		
		ResponseEntity<Map<String,String>> response = app.uploadFile(file, "malicious.exe", null);
		
		assertEquals(400, response.getStatusCodeValue());
		assertTrue(response.getBody().get("error").contains("File type not allowed"));
	}
	
	@Test
	void testUploadWithValidFile() {
		DropboxApplication app = new DropboxApplication();
		
		MockMultipartFile file = new MockMultipartFile(
			"file", 
			"test.txt", 
			"text/plain", 
			"test content".getBytes()
		);
		
		ResponseEntity<Map<String,String>> response = app.uploadFile(file, "test.txt", null);
		
		assertEquals(200, response.getStatusCodeValue());
		assertTrue(response.getBody().containsKey("file_id"));
	}
	
	@Test 
	void testUploadWithLargeFile() {
		DropboxApplication app = new DropboxApplication();
		
		// Create a large file that exceeds the 10MB limit
		byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
		MockMultipartFile file = new MockMultipartFile(
			"file", 
			"large.txt", 
			"text/plain", 
			largeContent
		);
		
		ResponseEntity<Map<String,String>> response = app.uploadFile(file, "large.txt", null);
		
		assertEquals(400, response.getStatusCodeValue());
		assertTrue(response.getBody().get("error").contains("File size exceeds"));
	}
	
	@Test
	void testUploadWithInvalidFileName() {
		DropboxApplication app = new DropboxApplication();
		
		MockMultipartFile file = new MockMultipartFile(
			"file", 
			"test.txt", 
			"text/plain", 
			"test content".getBytes()
		);
		
		// Test with path traversal in filename
		ResponseEntity<Map<String,String>> response = app.uploadFile(file, "../../../etc/passwd", null);
		
		assertEquals(400, response.getStatusCodeValue());
		assertTrue(response.getBody().get("error").contains("Invalid characters"));
	}
	
	@Test
	void testUploadWithEmptyFileName() {
		DropboxApplication app = new DropboxApplication();
		
		MockMultipartFile file = new MockMultipartFile(
			"file", 
			"test.txt", 
			"text/plain", 
			"test content".getBytes()
		);
		
		// Test with empty filename
		ResponseEntity<Map<String,String>> response = app.uploadFile(file, "", null);
		
		assertEquals(400, response.getStatusCodeValue());
		assertTrue(response.getBody().get("error").contains("cannot be empty"));
	}
	
	@Test
	void testUpdateWithInvalidFileType() {
		DropboxApplication app = new DropboxApplication();
		
		// First upload a valid file
		MockMultipartFile validFile = new MockMultipartFile(
			"file", 
			"test.txt", 
			"text/plain", 
			"test content".getBytes()
		);
		
		ResponseEntity<Map<String,String>> uploadResponse = app.uploadFile(validFile, "test.txt", null);
		String fileId = uploadResponse.getBody().get("file_id");
		
		// Try to update with invalid file type
		MockMultipartFile invalidFile = new MockMultipartFile(
			"file", 
			"malicious.exe", 
			"application/octet-stream", 
			"malicious content".getBytes()
		);
		
		ResponseEntity<?> updateResponse = app.updateFile(fileId, invalidFile, null);
		
		assertEquals(400, updateResponse.getStatusCodeValue());
	}

}