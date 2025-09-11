package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void testHeaderInjectionVulnerability() {
		// Test for HTTP Response Header Injection vulnerability
		// Upload a file with malicious filename containing newlines
		String maliciousFilename = "test.txt\r\nX-Injected-Header: malicious-value\r\n";
		
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource("test content".getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt"; // The actual file object filename
			}
		});
		body.add("file_name", maliciousFilename); // This is the parameter used in the header
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		// Upload the file
		ResponseEntity<String> uploadResponse = restTemplate.postForEntity(
			"http://localhost:" + port + "/files/upload", requestEntity, String.class);
		
		assertEquals(HttpStatus.OK, uploadResponse.getStatusCode());
		
		// Extract file ID from response
		String responseBody = uploadResponse.getBody();
		String fileId = responseBody.substring(responseBody.indexOf("\"file_id\":\"") + 11, 
											   responseBody.indexOf("\"", responseBody.indexOf("\"file_id\":\"") + 11));
		
		// Download the file and check if header injection was prevented
		ResponseEntity<byte[]> downloadResponse = restTemplate.getForEntity(
			"http://localhost:" + port + "/files/" + fileId, byte[].class);
		
		assertEquals(HttpStatus.OK, downloadResponse.getStatusCode());
		
		// Check Content-Disposition header
		String contentDisposition = downloadResponse.getHeaders().getFirst("Content-Disposition");
		assertNotNull(contentDisposition);
		
		System.out.println("Content-Disposition header: " + contentDisposition);
		
		// After fix: The header should not contain the malicious injection characters
		assertFalse(contentDisposition.contains("\r"), "Header should not contain carriage return");
		assertFalse(contentDisposition.contains("\n"), "Header should not contain line feed");
		
		// Verify the filename is properly quoted and sanitized
		// The dangerous \r\n characters should be replaced with underscores
		assertTrue(contentDisposition.contains("test.txt_"), 
				   "Filename should be sanitized with dangerous characters replaced");
		assertTrue(contentDisposition.startsWith("attachment; filename=\""), 
				   "Header should be properly quoted");
		assertTrue(contentDisposition.endsWith("\""), 
				   "Header should be properly quoted");
		
		// Most importantly: verify that no actual header injection occurred
		// The raw header value should be a single line without any actual injected headers
		String[] headerLines = contentDisposition.split("\r\n|\r|\n");
		assertEquals(1, headerLines.length, 
					"Content-Disposition header should be a single line without injected headers");
	}

	@Test
	void testNormalFileDownload() {
		// Test that normal file downloads still work correctly
		String normalFilename = "normal-file.txt";
		
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource("normal content".getBytes()) {
			@Override
			public String getFilename() {
				return "normal-file.txt";
			}
		});
		body.add("file_name", normalFilename);
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		// Upload the file
		ResponseEntity<String> uploadResponse = restTemplate.postForEntity(
			"http://localhost:" + port + "/files/upload", requestEntity, String.class);
		
		assertEquals(HttpStatus.OK, uploadResponse.getStatusCode());
		
		// Extract file ID from response
		String responseBody = uploadResponse.getBody();
		String fileId = responseBody.substring(responseBody.indexOf("\"file_id\":\"") + 11, 
											   responseBody.indexOf("\"", responseBody.indexOf("\"file_id\":\"") + 11));
		
		// Download the file
		ResponseEntity<byte[]> downloadResponse = restTemplate.getForEntity(
			"http://localhost:" + port + "/files/" + fileId, byte[].class);
		
		assertEquals(HttpStatus.OK, downloadResponse.getStatusCode());
		
		// Check Content-Disposition header
		String contentDisposition = downloadResponse.getHeaders().getFirst("Content-Disposition");
		assertNotNull(contentDisposition);
		assertEquals("attachment; filename=\"normal-file.txt\"", contentDisposition);
		
		// Check file content
		assertArrayEquals("normal content".getBytes(), downloadResponse.getBody());
	}
}
