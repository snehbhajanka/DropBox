package com.dropbox.application;

import org.junit.jupiter.api.Test;
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
public class SecurityTest {

    @LocalServerPort
    private int port;

    private TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    public void testFilenameInjectionVulnerability() {
        // Test filename with potentially malicious content that could cause header injection
        String maliciousFilename = "test.txt\r\nContent-Type: text/html\r\n\r\n<script>alert('XSS')</script>";
        
        // Upload a file with malicious filename
        String fileId = uploadTestFileWithMaliciousFilename(maliciousFilename);
        assertNotNull(fileId, "File upload should succeed");

        // Download the file and check if the response headers are vulnerable
        String url = "http://localhost:" + port + "/files/" + fileId;
        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        // Check if the Content-Disposition header contains the malicious filename without proper encoding
        String contentDisposition = response.getHeaders().getFirst("Content-Disposition");
        assertNotNull(contentDisposition, "Content-Disposition header should be present");
        
        // The vulnerability exists if the malicious content is present in the header without encoding
        if (contentDisposition.contains("\r\n")) {
            fail("VULNERABILITY DETECTED: Filename injection possible - newlines found in Content-Disposition header: " 
                + contentDisposition);
        }
        
        // The header should not contain unencoded special characters
        if (contentDisposition.contains("<script>")) {
            fail("VULNERABILITY DETECTED: Unencoded script content found in Content-Disposition header: " 
                + contentDisposition);
        }
    }

    @Test
    public void testNormalFilenameHandling() {
        // Test with a normal filename to ensure basic functionality works
        String normalFilename = "document.pdf";
        
        String fileId = uploadTestFileWithFilename(normalFilename);
        assertNotNull(fileId, "File upload should succeed");

        // Download the file
        String url = "http://localhost:" + port + "/files/" + fileId;
        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String contentDisposition = response.getHeaders().getFirst("Content-Disposition");
        assertNotNull(contentDisposition, "Content-Disposition header should be present");
        assertTrue(contentDisposition.contains(normalFilename), 
            "Content-Disposition should contain the filename");
    }

    private String uploadTestFileWithMaliciousFilename(String filename) {
        return uploadTestFileWithFilename(filename);
    }

    private String uploadTestFileWithFilename(String filename) {
        String url = "http://localhost:" + port + "/files/upload";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource("test content".getBytes()) {
            @Override
            public String getFilename() {
                return "upload.txt";
            }
        });
        body.add("file_name", filename);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
        
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return (String) response.getBody().get("file_id");
        }
        return null;
    }
}