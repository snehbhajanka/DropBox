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

import java.util.Map;

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
	void testValidFileUpload() {
		String url = "http://localhost:" + port + "/files/upload";
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		HttpHeaders fileHeaders = new HttpHeaders();
		fileHeaders.setContentType(MediaType.TEXT_PLAIN);
		HttpEntity<ByteArrayResource> fileEntity = new HttpEntity<>(
			new ByteArrayResource("test content".getBytes()) {
				@Override
				public String getFilename() {
					return "test.txt";
				}
			}, fileHeaders);
		
		body.add("file", fileEntity);
		body.add("file_name", "test.txt");
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
		
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody().get("file_id"));
	}

	@Test
	void testPathTraversalAttack() {
		String url = "http://localhost:" + port + "/files/upload";
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		HttpHeaders fileHeaders = new HttpHeaders();
		fileHeaders.setContentType(MediaType.TEXT_PLAIN);
		HttpEntity<ByteArrayResource> fileEntity = new HttpEntity<>(
			new ByteArrayResource("test content".getBytes()) {
				@Override
				public String getFilename() {
					return "test.txt";
				}
			}, fileHeaders);
		
		body.add("file", fileEntity);
		body.add("file_name", "../../../etc/passwd");
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
		
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertTrue(response.getBody().get("error").toString().contains("Invalid file name"));
	}

	@Test
	void testInvalidFileType() {
		String url = "http://localhost:" + port + "/files/upload";
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		HttpHeaders fileHeaders = new HttpHeaders();
		fileHeaders.setContentType(MediaType.parseMediaType("application/octet-stream"));
		HttpEntity<ByteArrayResource> fileEntity = new HttpEntity<>(
			new ByteArrayResource("test content".getBytes()) {
				@Override
				public String getFilename() {
					return "test.exe";
				}
			}, fileHeaders);
		
		body.add("file", fileEntity);
		body.add("file_name", "test.exe");
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
		
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertTrue(response.getBody().get("error").toString().contains("Invalid file type"));
	}

	@Test
	void testSlashInFileName() {
		String url = "http://localhost:" + port + "/files/upload";
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		HttpHeaders fileHeaders = new HttpHeaders();
		fileHeaders.setContentType(MediaType.TEXT_PLAIN);
		HttpEntity<ByteArrayResource> fileEntity = new HttpEntity<>(
			new ByteArrayResource("test content".getBytes()) {
				@Override
				public String getFilename() {
					return "test.txt";
				}
			}, fileHeaders);
		
		body.add("file", fileEntity);
		body.add("file_name", "folder/test.txt");
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
		
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertTrue(response.getBody().get("error").toString().contains("Invalid file name"));
	}

	@Test
	void testBackslashInFileName() {
		String url = "http://localhost:" + port + "/files/upload";
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		HttpHeaders fileHeaders = new HttpHeaders();
		fileHeaders.setContentType(MediaType.TEXT_PLAIN);
		HttpEntity<ByteArrayResource> fileEntity = new HttpEntity<>(
			new ByteArrayResource("test content".getBytes()) {
				@Override
				public String getFilename() {
					return "test.txt";
				}
			}, fileHeaders);
		
		body.add("file", fileEntity);
		body.add("file_name", "folder\\test.txt");
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
		
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertTrue(response.getBody().get("error").toString().contains("Invalid file name"));
	}
}
