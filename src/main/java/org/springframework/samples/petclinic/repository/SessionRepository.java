package org.springframework.samples.petclinic.repository;

import org.springframework.session.Session;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for session management operations.
 * Provides abstraction for session storage and retrieval.
 */
public interface SessionRepository {

    /**
     * Find a session by its ID.
     *
     * @param sessionId the session ID
     * @return Optional containing the session if found, empty otherwise
     */
    Optional<Session> findById(String sessionId);

    /**
     * Save a session.
     *
     * @param session the session to save
     */
    void save(Session session);

    /**
     * Delete a session by its ID.
     *
     * @param sessionId the session ID to delete
     */
    void deleteById(String sessionId);

    /**
     * Get all attribute names for a session.
     *
     * @param sessionId the session ID
     * @return set of attribute names
     */
    Map<String, Object> getSessionAttributes(String sessionId);

    /**
     * Get a specific attribute from a session.
     *
     * @param sessionId the session ID
     * @param attributeName the attribute name
     * @return the attribute value, or null if not found
     */
    Object getSessionAttribute(String sessionId, String attributeName);

    /**
     * Set an attribute in a session.
     *
     * @param sessionId the session ID
     * @param attributeName the attribute name
     * @param attributeValue the attribute value
     */
    void setSessionAttribute(String sessionId, String attributeName, Object attributeValue);

    /**
     * Remove an attribute from a session.
     *
     * @param sessionId the session ID
     * @param attributeName the attribute name
     */
    void removeSessionAttribute(String sessionId, String attributeName);

    /**
     * Check if a session exists.
     *
     * @param sessionId the session ID
     * @return true if session exists, false otherwise
     */
    boolean existsById(String sessionId);

    /**
     * Get session creation time.
     *
     * @param sessionId the session ID
     * @return creation time in milliseconds, or null if session not found
     */
    Long getSessionCreationTime(String sessionId);

    /**
     * Get session last accessed time.
     *
     * @param sessionId the session ID
     * @return last accessed time in milliseconds, or null if session not found
     */
    Long getSessionLastAccessedTime(String sessionId);

    /**
     * Get session max inactive interval.
     *
     * @param sessionId the session ID
     * @return max inactive interval in seconds, or null if session not found
     */
    Integer getSessionMaxInactiveInterval(String sessionId);

    /**
     * Get the number of attributes in a session.
     *
     * @param sessionId the session ID
     * @return number of attributes, or 0 if session not found
     */
    int getSessionAttributeCount(String sessionId);
}