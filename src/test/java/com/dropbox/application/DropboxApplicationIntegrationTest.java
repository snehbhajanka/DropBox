package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DropboxApplicationIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void testListFiles() {
		String url = "http://localhost:" + port + "/files";
		ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	@Test
	void testFileNotFound() {
		String url = "http://localhost:" + port + "/files/nonexistent";
		ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}
}