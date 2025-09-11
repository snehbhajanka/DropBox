package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DropboxApplicationTests {

	@LocalServerPort
	private int port;

	private TestRestTemplate restTemplate = new TestRestTemplate();

	@Test
	void contextLoads() {
	}

	@Test
	void testHttpHeaderInjectionVulnerability() {
		// Create a malicious filename that contains CRLF characters for header injection
		String maliciousFilename = "test.txt\r\nSet-Cookie: malicious=true\r\nContent-Type: text/html";
		
		// Upload a file with malicious filename
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource("test content".getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt";
			}
		});
		body.add("file_name", maliciousFilename);
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		ResponseEntity<String> uploadResponse = restTemplate.postForEntity(
			"http://localhost:" + port + "/files/upload", requestEntity, String.class);
		
		assertEquals(HttpStatus.OK, uploadResponse.getStatusCode());
		assertTrue(uploadResponse.getBody().contains("file_id"));
		
		// Extract file_id from response
		String responseBody = uploadResponse.getBody();
		String fileId = responseBody.substring(responseBody.indexOf("file_id\":\"") + 10, 
			responseBody.indexOf("\"", responseBody.indexOf("file_id\":\"") + 10));
		
		// Now try to download the file and check for header injection
		ResponseEntity<byte[]> downloadResponse = restTemplate.getForEntity(
			"http://localhost:" + port + "/files/" + fileId, byte[].class);
		
		assertEquals(HttpStatus.OK, downloadResponse.getStatusCode());
		
		// Check if the Content-Disposition header contains the malicious content
		String contentDisposition = downloadResponse.getHeaders().getFirst("Content-Disposition");
		assertNotNull(contentDisposition);
		
		// Verify that CRLF characters are sanitized - they should not be present
		assertFalse(contentDisposition.contains("\r"), 
			"Content-Disposition header should not contain carriage return characters");
		assertFalse(contentDisposition.contains("\n"), 
			"Content-Disposition header should not contain newline characters");
		
		// Verify the filename is properly quoted
		assertTrue(contentDisposition.startsWith("attachment; filename=\""), 
			"Content-Disposition should have properly quoted filename");
		assertTrue(contentDisposition.endsWith("\""), 
			"Content-Disposition should end with closing quote");
		
		// Verify that the dangerous parts have been removed/sanitized
		String extractedFilename = contentDisposition.substring(
			contentDisposition.indexOf("filename=\"") + 10, 
			contentDisposition.lastIndexOf("\""));
		
		// The malicious parts should be sanitized - no CRLF injection possible
		assertFalse(extractedFilename.contains("\r\nSet-Cookie:"), 
			"Filename should not contain CRLF injection attempts");
		assertFalse(extractedFilename.contains("\r\nContent-Type:"), 
			"Filename should not contain CRLF injection attempts");
	}

	@Test
	void testNormalFilenameHandling() {
		// Test that normal filenames work correctly
		String normalFilename = "document.pdf";
		
		// Upload a file with normal filename
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource("test content".getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt";
			}
		});
		body.add("file_name", normalFilename);
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		ResponseEntity<String> uploadResponse = restTemplate.postForEntity(
			"http://localhost:" + port + "/files/upload", requestEntity, String.class);
		
		assertEquals(HttpStatus.OK, uploadResponse.getStatusCode());
		assertTrue(uploadResponse.getBody().contains("file_id"));
		
		// Extract file_id from response
		String responseBody = uploadResponse.getBody();
		String fileId = responseBody.substring(responseBody.indexOf("file_id\":\"") + 10, 
			responseBody.indexOf("\"", responseBody.indexOf("file_id\":\"") + 10));
		
		// Download the file and verify normal functionality
		ResponseEntity<byte[]> downloadResponse = restTemplate.getForEntity(
			"http://localhost:" + port + "/files/" + fileId, byte[].class);
		
		assertEquals(HttpStatus.OK, downloadResponse.getStatusCode());
		
		// Check the Content-Disposition header
		String contentDisposition = downloadResponse.getHeaders().getFirst("Content-Disposition");
		assertNotNull(contentDisposition);
		
		// Verify proper formatting
		assertEquals("attachment; filename=\"" + normalFilename + "\"", contentDisposition);
		
		// Verify content
		assertEquals("test content", new String(downloadResponse.getBody()));
	}

}
