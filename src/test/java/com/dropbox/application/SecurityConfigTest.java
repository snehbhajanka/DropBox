package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void testSecurityHeaders() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        
        mockMvc.perform(get("/files"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-XSS-Protection", "1; mode=block"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Content-Security-Policy", 
                    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:"));
    }

    @Test
    void testFileEndpointFunctionality() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        
        mockMvc.perform(get("/files"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value("No files to display"));
    }
}