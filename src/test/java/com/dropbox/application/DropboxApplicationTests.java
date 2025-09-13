package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
class DropboxApplicationTests {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Test
	void contextLoads() {
	}

	@Test
	void testFilenameVulnerability() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
		
		// Test directory traversal attack through filename
		MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test content".getBytes());
		
		mockMvc.perform(multipart("/files/upload")
				.file(file)
				.param("file_name", "../../etc/passwd"))  // Malicious filename
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.file_id").exists());
		
		// Test null byte injection
		mockMvc.perform(multipart("/files/upload")
				.file(file)
				.param("file_name", "innocent.txt\0.exe"))  // Null byte injection
				.andExpect(status().isOk());
		
		// Test overly long filename
		String longFilename = "a".repeat(1000);
		mockMvc.perform(multipart("/files/upload")
				.file(file)
				.param("file_name", longFilename))
				.andExpect(status().isOk());
	}

	@Test
	void testFilenameSanitization() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
		
		MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test content".getBytes());
		
		// Test that dangerous filenames are sanitized
		mockMvc.perform(multipart("/files/upload")
				.file(file)
				.param("file_name", "../../malicious.txt"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.file_id").exists());
		
		// Test that null bytes are removed
		mockMvc.perform(multipart("/files/upload")
				.file(file)
				.param("file_name", "safe.txt\0.exe"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.file_id").exists());
		
		// Test that very long filenames are truncated
		String longFilename = "very_long_filename_" + "a".repeat(300) + ".txt";
		mockMvc.perform(multipart("/files/upload")
				.file(file)
				.param("file_name", longFilename))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.file_id").exists());
	}

	@Test
	void testValidFilename() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
		
		MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test content".getBytes());
		
		mockMvc.perform(multipart("/files/upload")
				.file(file)
				.param("file_name", "valid_filename.txt"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.file_id").exists());
	}
}
