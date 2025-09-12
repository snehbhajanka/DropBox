package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class SecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testPathTraversalInFileName() throws Exception {
        // Test that path traversal attempts in file names are blocked
        MockMultipartFile maliciousFile = new MockMultipartFile(
            "file", 
            "../../../etc/passwd", 
            "text/plain", 
            "malicious content".getBytes()
        );

        mockMvc.perform(multipart("/files/upload")
                .file(maliciousFile)
                .param("file_name", "../../../etc/passwd"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testExecutableFileUpload() throws Exception {
        // Test that executable files are blocked
        MockMultipartFile executableFile = new MockMultipartFile(
            "file", 
            "malware.exe", 
            "application/octet-stream", 
            "fake executable content".getBytes()
        );

        mockMvc.perform(multipart("/files/upload")
                .file(executableFile)
                .param("file_name", "malware.exe"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testValidFileUpload() throws Exception {
        // Test that valid files are still accepted
        MockMultipartFile validFile = new MockMultipartFile(
            "file", 
            "document.txt", 
            "text/plain", 
            "safe content".getBytes()
        );

        mockMvc.perform(multipart("/files/upload")
                .file(validFile)
                .param("file_name", "document.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file_id").exists());
    }

    @Test
    public void testFileNameSanitization() throws Exception {
        // Test that special characters in file names are handled properly
        MockMultipartFile fileWithSpecialChars = new MockMultipartFile(
            "file", 
            "file<script>alert('xss')</script>.txt", 
            "text/plain", 
            "content".getBytes()
        );

        mockMvc.perform(multipart("/files/upload")
                .file(fileWithSpecialChars)
                .param("file_name", "file<script>alert('xss')</script>.txt"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testEmptyFileName() throws Exception {
        // Test that empty file names are rejected
        MockMultipartFile fileWithEmptyName = new MockMultipartFile(
            "file", 
            "", 
            "text/plain", 
            "content".getBytes()
        );

        mockMvc.perform(multipart("/files/upload")
                .file(fileWithEmptyName)
                .param("file_name", ""))
                .andExpect(status().isBadRequest());
    }
}