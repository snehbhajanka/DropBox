package com.dropbox.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SecurityUtilsTest {

    @Test
    public void testSanitizeFilename() {
        // Test normal filename
        assertEquals("test.txt", SecurityUtils.sanitizeFilename("test.txt"));
        
        // Test CRLF injection removal
        assertEquals("test.txtX-Injected-Header_ malicious", 
                    SecurityUtils.sanitizeFilename("test.txt\r\nX-Injected-Header: malicious"));
        
        // Test path traversal removal
        assertEquals("etc_passwd", SecurityUtils.sanitizeFilename("../../../etc/passwd"));
        
        // Test null/empty handling
        assertEquals("unnamed_file", SecurityUtils.sanitizeFilename(null));
        assertEquals("unnamed_file", SecurityUtils.sanitizeFilename(""));
        assertEquals("unnamed_file", SecurityUtils.sanitizeFilename("   "));
        
        // Test length limit
        String longFilename = "a".repeat(300);
        String sanitized = SecurityUtils.sanitizeFilename(longFilename);
        assertEquals(255, sanitized.length());
    }

    @Test
    public void testIsValidFilename() {
        // Valid filenames
        assertTrue(SecurityUtils.isValidFilename("test.txt"));
        assertTrue(SecurityUtils.isValidFilename("my_file-2023.pdf"));
        assertTrue(SecurityUtils.isValidFilename("document with spaces.docx"));
        
        // Invalid filenames
        assertFalse(SecurityUtils.isValidFilename("test.txt\r\nX-Header: bad"));
        assertFalse(SecurityUtils.isValidFilename("../../../etc/passwd"));
        assertFalse(SecurityUtils.isValidFilename("file<script>alert(1)</script>.txt"));
        assertFalse(SecurityUtils.isValidFilename(null));
        assertFalse(SecurityUtils.isValidFilename(""));
    }

    @Test
    public void testIsValidFileSize() {
        long maxSize = 10 * 1024 * 1024; // 10MB
        
        // Valid sizes
        assertTrue(SecurityUtils.isValidFileSize(1024, maxSize));
        assertTrue(SecurityUtils.isValidFileSize(maxSize, maxSize));
        
        // Invalid sizes
        assertFalse(SecurityUtils.isValidFileSize(0, maxSize));
        assertFalse(SecurityUtils.isValidFileSize(-1, maxSize));
        assertFalse(SecurityUtils.isValidFileSize(maxSize + 1, maxSize));
    }
}