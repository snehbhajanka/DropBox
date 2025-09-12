package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SpringJUnitConfig
class DropboxApplicationTests {

	@LocalServerPort
	private int port;

	private TestRestTemplate restTemplate = new TestRestTemplate();

	@Test
	void contextLoads() {
	}

	@Test
	void testHeaderInjectionPrevention() {
		// Test that malicious filenames with control characters are sanitized
		String maliciousFilename = "test\r\nX-Malicious-Header: evil\r\nContent-Type: text/html\r\n\r\n<script>alert('xss')</script>";
		
		// Upload a file with malicious filename
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource("test content".getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt";
			}
		});
		body.add("file_name", maliciousFilename);
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		ResponseEntity<String> uploadResponse = restTemplate.postForEntity(
			"http://localhost:" + port + "/files/upload", requestEntity, String.class);
		
		assertEquals(HttpStatus.OK, uploadResponse.getStatusCode());
		
		// Extract file ID from response
		String responseBody = uploadResponse.getBody();
		assertTrue(responseBody.contains("file_id"));
		String fileId = responseBody.split("\"file_id\":\"")[1].split("\"")[0];
		
		// Download the file and check that the Content-Disposition header is safe
		ResponseEntity<byte[]> downloadResponse = restTemplate.getForEntity(
			"http://localhost:" + port + "/files/" + fileId, byte[].class);
		
		assertEquals(HttpStatus.OK, downloadResponse.getStatusCode());
		
		// Verify that the Content-Disposition header doesn't contain control characters
		String contentDisposition = downloadResponse.getHeaders().getFirst("Content-Disposition");
		assertNotNull(contentDisposition);
		
		// Debug: print the actual header
		System.out.println("Content-Disposition header: " + contentDisposition);
		
		// CRITICAL SECURITY CHECKS: Should not contain carriage return or newline characters
		// These are what allow HTTP Response Splitting attacks
		assertFalse(contentDisposition.contains("\r"), "Header should not contain carriage return");
		assertFalse(contentDisposition.contains("\n"), "Header should not contain newline");
		
		// Should be properly quoted to prevent header injection
		assertTrue(contentDisposition.contains("filename=\""));
		
		// The most important security check: should not contain control characters that allow header injection
		assertFalse(contentDisposition.contains("\r\n"), "Should not contain CRLF sequence that enables header injection");
		
		// Should not contain dangerous control characters that could break out of the quoted value
		assertFalse(contentDisposition.contains("\";\r\n"));
		assertFalse(contentDisposition.contains("\";"));
		
		// Verify that even if the original contained malicious content, it cannot break out of the filename value
		// The presence of the text is OK as long as it's safely contained within the quoted filename
		assertTrue(contentDisposition.startsWith("attachment; filename=\""));
		assertTrue(contentDisposition.endsWith("\""));
	}

	@Test
	void testEmptyFilenameValidation() {
		// Test that empty filenames are rejected
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource("test content".getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt";
			}
		});
		body.add("file_name", "");
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		ResponseEntity<String> response = restTemplate.postForEntity(
			"http://localhost:" + port + "/files/upload", requestEntity, String.class);
		
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertTrue(response.getBody().contains("File name cannot be empty"));
	}

	@Test
	void testInvalidFilenameValidation() {
		// Test that filenames with only control characters are rejected
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource("test content".getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt";
			}
		});
		body.add("file_name", "\r\n\t");
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		ResponseEntity<String> response = restTemplate.postForEntity(
			"http://localhost:" + port + "/files/upload", requestEntity, String.class);
		
		// Debug: print the actual response
		System.out.println("Response status: " + response.getStatusCode());
		System.out.println("Response body: " + response.getBody());
		
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		// The control characters trim to empty, so we get "File name cannot be empty" message
		assertTrue(response.getBody().contains("File name cannot be empty"));
	}
}
