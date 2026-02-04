package org.springframework.samples.petclinic.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.session.Session;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SessionRepository implementations.
 */
class SessionRepositoryTests {

    private TestSessionRepository sessionRepository;
    private TestSession testSession;

    @BeforeEach
    void setUp() {
        sessionRepository = new TestSessionRepository();
        testSession = sessionRepository.createSession("test-session-id");
    }

    @Test
    void testCreateAndFindSession() {
        // Test session creation and retrieval
        Optional<Session> foundSession = sessionRepository.findById("test-session-id");
        assertTrue(foundSession.isPresent());
        assertEquals("test-session-id", foundSession.get().getId());
    }

    @Test
    void testSessionNotFound() {
        Optional<Session> foundSession = sessionRepository.findById("non-existent-session");
        assertFalse(foundSession.isPresent());
    }

    @Test
    void testSetAndGetSessionAttribute() {
        // Set an attribute
        sessionRepository.setSessionAttribute("test-session-id", "user_id", "12345");
        
        // Retrieve the attribute
        Object value = sessionRepository.getSessionAttribute("test-session-id", "user_id");
        assertEquals("12345", value);
    }

    @Test
    void testGetAllSessionAttributes() {
        // Set multiple attributes
        sessionRepository.setSessionAttribute("test-session-id", "user_id", "12345");
        sessionRepository.setSessionAttribute("test-session-id", "username", "testuser");
        sessionRepository.setSessionAttribute("test-session-id", "role", "USER");
        
        // Get all attributes
        Map<String, Object> attributes = sessionRepository.getSessionAttributes("test-session-id");
        assertEquals(3, attributes.size());
        assertEquals("12345", attributes.get("user_id"));
        assertEquals("testuser", attributes.get("username"));
        assertEquals("USER", attributes.get("role"));
    }

    @Test
    void testRemoveSessionAttribute() {
        // Set an attribute
        sessionRepository.setSessionAttribute("test-session-id", "temp_data", "temporary");
        
        // Verify it exists
        assertNotNull(sessionRepository.getSessionAttribute("test-session-id", "temp_data"));
        
        // Remove the attribute
        sessionRepository.removeSessionAttribute("test-session-id", "temp_data");
        
        // Verify it's removed
        assertNull(sessionRepository.getSessionAttribute("test-session-id", "temp_data"));
    }

    @Test
    void testSessionExists() {
        assertTrue(sessionRepository.existsById("test-session-id"));
        assertFalse(sessionRepository.existsById("non-existent-session"));
    }

    @Test
    void testDeleteSession() {
        // Verify session exists
        assertTrue(sessionRepository.existsById("test-session-id"));
        
        // Delete the session
        sessionRepository.deleteById("test-session-id");
        
        // Verify session is deleted
        assertFalse(sessionRepository.existsById("test-session-id"));
    }

    @Test
    void testSessionTiming() {
        Long creationTime = sessionRepository.getSessionCreationTime("test-session-id");
        Long lastAccessedTime = sessionRepository.getSessionLastAccessedTime("test-session-id");
        Integer maxInactiveInterval = sessionRepository.getSessionMaxInactiveInterval("test-session-id");
        
        assertNotNull(creationTime);
        assertNotNull(lastAccessedTime);
        assertNotNull(maxInactiveInterval);
        assertTrue(creationTime > 0);
        assertTrue(lastAccessedTime > 0);
        assertEquals(1800, maxInactiveInterval); // 30 minutes in seconds
    }

    @Test
    void testSessionAttributeCount() {
        // Initially no attributes
        assertEquals(0, sessionRepository.getSessionAttributeCount("test-session-id"));
        
        // Add some attributes
        sessionRepository.setSessionAttribute("test-session-id", "attr1", "value1");
        sessionRepository.setSessionAttribute("test-session-id", "attr2", "value2");
        
        // Check count
        assertEquals(2, sessionRepository.getSessionAttributeCount("test-session-id"));
        
        // Remove one attribute
        sessionRepository.removeSessionAttribute("test-session-id", "attr1");
        
        // Check count again
        assertEquals(1, sessionRepository.getSessionAttributeCount("test-session-id"));
    }

    @Test
    void testSessionExpiration() {
        // Create a session with short expiration
        TestSession shortSession = new TestSession("short-session");
        shortSession.setMaxInactiveInterval(Duration.ofSeconds(1));
        sessionRepository.save(shortSession);
        
        // Session should not be expired initially
        assertFalse(shortSession.isExpired());
        
        // Wait for expiration (in real tests, you might mock time)
        try {
            Thread.sleep(1100); // Wait 1.1 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Session should now be expired
        assertTrue(shortSession.isExpired());
    }

    @Test
    void testClearAllSessions() {
        // Create multiple sessions
        sessionRepository.createSession("session1");
        sessionRepository.createSession("session2");
        sessionRepository.createSession("session3");
        
        // Verify sessions exist
        assertEquals(4, sessionRepository.getSessionCount()); // Including the setUp session
        
        // Clear all sessions
        sessionRepository.clearAllSessions();
        
        // Verify all sessions are cleared
        assertEquals(0, sessionRepository.getSessionCount());
    }

    @Test
    void testSessionAttributeTypes() {
        String sessionId = "test-session-id";
        
        // Test different attribute types
        sessionRepository.setSessionAttribute(sessionId, "string_attr", "string_value");
        sessionRepository.setSessionAttribute(sessionId, "int_attr", 42);
        sessionRepository.setSessionAttribute(sessionId, "boolean_attr", true);
        sessionRepository.setSessionAttribute(sessionId, "null_attr", null);
        
        // Verify types are preserved
        assertEquals("string_value", sessionRepository.getSessionAttribute(sessionId, "string_attr"));
        assertEquals(42, sessionRepository.getSessionAttribute(sessionId, "int_attr"));
        assertEquals(true, sessionRepository.getSessionAttribute(sessionId, "boolean_attr"));
        assertNull(sessionRepository.getSessionAttribute(sessionId, "null_attr"));
    }

    @Test
    void testNonExistentSessionOperations() {
        String nonExistentSessionId = "non-existent";
        
        // Operations on non-existent session should handle gracefully
        assertNull(sessionRepository.getSessionAttribute(nonExistentSessionId, "any_attr"));
        assertTrue(sessionRepository.getSessionAttributes(nonExistentSessionId).isEmpty());
        assertEquals(0, sessionRepository.getSessionAttributeCount(nonExistentSessionId));
        assertNull(sessionRepository.getSessionCreationTime(nonExistentSessionId));
        assertNull(sessionRepository.getSessionLastAccessedTime(nonExistentSessionId));
        assertNull(sessionRepository.getSessionMaxInactiveInterval(nonExistentSessionId));
        
        // These operations should not throw exceptions
        assertDoesNotThrow(() -> {
            sessionRepository.setSessionAttribute(nonExistentSessionId, "attr", "value");
            sessionRepository.removeSessionAttribute(nonExistentSessionId, "attr");
            sessionRepository.deleteById(nonExistentSessionId);
        });
    }
}