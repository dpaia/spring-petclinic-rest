package org.springframework.samples.petclinic.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation of SessionRepository using Spring Session.
 */
@Repository
public class SessionRepositoryImpl implements SessionRepository {

    @SuppressWarnings("rawtypes")
    private final FindByIndexNameSessionRepository sessionRepository;

    @Autowired
    @SuppressWarnings("rawtypes")
    public SessionRepositoryImpl(FindByIndexNameSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public Optional<Session> findById(String sessionId) {
        try {
            Session session = sessionRepository.findById(sessionId);
            return Optional.ofNullable(session);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Session session) {
        sessionRepository.save(session);
    }

    @Override
    public void deleteById(String sessionId) {
        sessionRepository.deleteById(sessionId);
    }

    @Override
    public Map<String, Object> getSessionAttributes(String sessionId) {
        Optional<Session> sessionOpt = findById(sessionId);
        if (sessionOpt.isPresent()) {
            Session session = sessionOpt.get();
            Map<String, Object> attributes = new HashMap<>();
            Set<String> attributeNames = session.getAttributeNames();
            for (String attributeName : attributeNames) {
                attributes.put(attributeName, session.getAttribute(attributeName));
            }
            return attributes;
        }
        return new HashMap<>();
    }

    @Override
    public Object getSessionAttribute(String sessionId, String attributeName) {
        Optional<Session> sessionOpt = findById(sessionId);
        if (sessionOpt.isPresent()) {
            return sessionOpt.get().getAttribute(attributeName);
        }
        return null;
    }

    @Override
    public void setSessionAttribute(String sessionId, String attributeName, Object attributeValue) {
        Optional<Session> sessionOpt = findById(sessionId);
        if (sessionOpt.isPresent()) {
            Session session = sessionOpt.get();
            session.setAttribute(attributeName, attributeValue);
            save(session);
        }
    }

    @Override
    public void removeSessionAttribute(String sessionId, String attributeName) {
        Optional<Session> sessionOpt = findById(sessionId);
        if (sessionOpt.isPresent()) {
            Session session = sessionOpt.get();
            session.removeAttribute(attributeName);
            save(session);
        }
    }

    @Override
    public boolean existsById(String sessionId) {
        return findById(sessionId).isPresent();
    }

    @Override
    public Long getSessionCreationTime(String sessionId) {
        Optional<Session> sessionOpt = findById(sessionId);
        if (sessionOpt.isPresent()) {
            return sessionOpt.get().getCreationTime().toEpochMilli();
        }
        return null;
    }

    @Override
    public Long getSessionLastAccessedTime(String sessionId) {
        Optional<Session> sessionOpt = findById(sessionId);
        if (sessionOpt.isPresent()) {
            return sessionOpt.get().getLastAccessedTime().toEpochMilli();
        }
        return null;
    }

    @Override
    public Integer getSessionMaxInactiveInterval(String sessionId) {
        Optional<Session> sessionOpt = findById(sessionId);
        if (sessionOpt.isPresent()) {
            Duration maxInactiveInterval = sessionOpt.get().getMaxInactiveInterval();
            return (int) maxInactiveInterval.getSeconds();
        }
        return null;
    }

    @Override
    public int getSessionAttributeCount(String sessionId) {
        Optional<Session> sessionOpt = findById(sessionId);
        if (sessionOpt.isPresent()) {
            return sessionOpt.get().getAttributeNames().size();
        }
        return 0;
    }
}