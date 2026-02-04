package org.springframework.samples.petclinic.repository;

import org.springframework.session.Session;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test implementation of SessionRepository for testing purposes.
 * Uses in-memory storage to simulate session operations.
 */
@Repository
public class TestSessionRepository implements SessionRepository {

    private final Map<String, TestSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<Session> findById(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public void save(Session session) {
        if (session instanceof TestSession) {
            sessions.put(session.getId(), (TestSession) session);
        }
    }

    @Override
    public void deleteById(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public Map<String, Object> getSessionAttributes(String sessionId) {
        TestSession session = sessions.get(sessionId);
        if (session != null) {
            return new HashMap<>(session.getAttributes());
        }
        return new HashMap<>();
    }

    @Override
    public Object getSessionAttribute(String sessionId, String attributeName) {
        TestSession session = sessions.get(sessionId);
        if (session != null) {
            return session.getAttribute(attributeName);
        }
        return null;
    }

    @Override
    public void setSessionAttribute(String sessionId, String attributeName, Object attributeValue) {
        TestSession session = sessions.get(sessionId);
        if (session != null) {
            session.setAttribute(attributeName, attributeValue);
        }
    }

    @Override
    public void removeSessionAttribute(String sessionId, String attributeName) {
        TestSession session = sessions.get(sessionId);
        if (session != null) {
            session.removeAttribute(attributeName);
        }
    }

    @Override
    public boolean existsById(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    @Override
    public Long getSessionCreationTime(String sessionId) {
        TestSession session = sessions.get(sessionId);
        if (session != null) {
            return session.getCreationTime().toEpochMilli();
        }
        return null;
    }

    @Override
    public Long getSessionLastAccessedTime(String sessionId) {
        TestSession session = sessions.get(sessionId);
        if (session != null) {
            return session.getLastAccessedTime().toEpochMilli();
        }
        return null;
    }

    @Override
    public Integer getSessionMaxInactiveInterval(String sessionId) {
        TestSession session = sessions.get(sessionId);
        if (session != null) {
            return (int) session.getMaxInactiveInterval().getSeconds();
        }
        return null;
    }

    @Override
    public int getSessionAttributeCount(String sessionId) {
        TestSession session = sessions.get(sessionId);
        if (session != null) {
            return session.getAttributeNames().size();
        }
        return 0;
    }

    /**
     * Create a new test session.
     *
     * @param sessionId the session ID
     * @return the created test session
     */
    public TestSession createSession(String sessionId) {
        TestSession session = new TestSession(sessionId);
        sessions.put(sessionId, session);
        return session;
    }

    /**
     * Clear all sessions (useful for test cleanup).
     */
    public void clearAllSessions() {
        sessions.clear();
    }

    /**
     * Get the number of active sessions.
     *
     * @return number of active sessions
     */
    public int getSessionCount() {
        return sessions.size();
    }
}