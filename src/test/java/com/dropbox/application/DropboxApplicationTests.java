package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DropboxApplicationTests {

	@LocalServerPort
	private int port;

	private TestRestTemplate restTemplate = new TestRestTemplate();

	@Test
	void contextLoads() {
	}

	@Test
	void testHeaderInjectionVulnerabilityIsFixed() {
		// Test that malicious filenames are sanitized to prevent header injection
		String maliciousFilename = "test\r\nLocation: http://evil.com\r\nContent-Type: text/html\r\n\r\n<script>alert('xss')</script>";
		
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
		
		assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		
		// Extract file ID from response
		String responseBody = uploadResponse.getBody();
		String fileId = responseBody.substring(responseBody.indexOf(":\"") + 2, responseBody.lastIndexOf("\""));
		
		// Download the file and check that the Content-Disposition header is sanitized
		ResponseEntity<byte[]> downloadResponse = restTemplate.getForEntity(
				"http://localhost:" + port + "/files/" + fileId, byte[].class);
		
		assertThat(downloadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		
		// Verify that the Content-Disposition header does not contain malicious content
		String contentDisposition = downloadResponse.getHeaders().getFirst("Content-Disposition");
		assertThat(contentDisposition).isNotNull();
		assertThat(contentDisposition).doesNotContain("\r");
		assertThat(contentDisposition).doesNotContain("\n");
		assertThat(contentDisposition).doesNotContain("Location:");
		assertThat(contentDisposition).doesNotContain("<script>");
		assertThat(contentDisposition).startsWith("attachment; filename=\"");
		assertThat(contentDisposition).endsWith("\"");
	}

}
