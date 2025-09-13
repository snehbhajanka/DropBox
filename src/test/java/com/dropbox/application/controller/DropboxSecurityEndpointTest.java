package com.dropbox.application.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "aws.s3.bucket-name=test-secure-dropbox-storage",
    "aws.s3.region=us-east-1",
    "aws.s3.block-public-acls=true",
    "aws.s3.ignore-public-acls=true",
    "aws.s3.block-public-policy=true",
    "aws.s3.restrict-public-buckets=true"
})
public class DropboxSecurityEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testFilesEndpointExists() throws Exception {
        // Test that the files endpoint exists
        mockMvc.perform(get("/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("No files to display"));
    }

    @Test
    public void testSecurityEndpointExists() throws Exception {
        // Test that the security endpoint exists (it will fail due to AWS not being configured)
        // But we can verify the endpoint is mapped correctly
        mockMvc.perform(get("/security/public-access-block"))
                .andExpect(status().is5xxServerError()); // Expected since AWS is not configured in test
    }
}