package org.springframework.samples.petclinic.repository;

import org.springframework.session.Session;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Test implementation of Spring Session for testing purposes.
 */
public class TestSession implements Session {

    private final String id;
    private final Instant creationTime;
    private Instant lastAccessedTime;
    private Duration maxInactiveInterval;
    private final Map<String, Object> attributes;
    private boolean expired;

    public TestSession() {
        this(UUID.randomUUID().toString());
    }

    public TestSession(String id) {
        this.id = id;
        this.creationTime = Instant.now();
        this.lastAccessedTime = this.creationTime;
        this.maxInactiveInterval = Duration.ofMinutes(30); // Default 30 minutes
        this.attributes = new HashMap<>();
        this.expired = false;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String changeSessionId() {
        // For testing purposes, we'll just return the same ID
        return id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String attributeName) {
        return (T) attributes.get(attributeName);
    }

    @Override
    public Set<String> getAttributeNames() {
        return attributes.keySet();
    }

    @Override
    public void setAttribute(String attributeName, Object attributeValue) {
        if (attributeValue == null) {
            removeAttribute(attributeName);
        } else {
            attributes.put(attributeName, attributeValue);
        }
    }

    @Override
    public void removeAttribute(String attributeName) {
        attributes.remove(attributeName);
    }

    @Override
    public Instant getCreationTime() {
        return creationTime;
    }

    @Override
    public void setLastAccessedTime(Instant lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }

    @Override
    public Instant getLastAccessedTime() {
        return lastAccessedTime;
    }

    @Override
    public void setMaxInactiveInterval(Duration interval) {
        this.maxInactiveInterval = interval;
    }

    @Override
    public Duration getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    @Override
    public boolean isExpired() {
        return expired || (maxInactiveInterval != null && 
                          Duration.between(lastAccessedTime, Instant.now()).compareTo(maxInactiveInterval) > 0);
    }

    /**
     * Mark this session as expired.
     */
    public void expire() {
        this.expired = true;
    }

    /**
     * Get all attributes as a map (for testing purposes).
     *
     * @return map of all attributes
     */
    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }

    /**
     * Update the last accessed time to now.
     */
    public void touch() {
        setLastAccessedTime(Instant.now());
    }

    @Override
    public String toString() {
        return "TestSession{" +
                "id='" + id + '\'' +
                ", creationTime=" + creationTime +
                ", lastAccessedTime=" + lastAccessedTime +
                ", maxInactiveInterval=" + maxInactiveInterval +
                ", attributeCount=" + attributes.size() +
                ", expired=" + expired +
                '}';
    }
}