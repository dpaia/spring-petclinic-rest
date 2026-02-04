package org.springframework.samples.petclinic.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.samples.petclinic.model.User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Service
public class SessionManagementService {

    public User getAuthenticatedUser(HttpSession session) {
        return (User) session.getAttribute("authenticated_user");
    }

    public void setAuthenticatedUser(HttpSession session, User user) {
        session.setAttribute("authenticated_user", user);
        updateLastActivityTime(session);
    }

    public Map<String, Object> getSessionInfo(HttpSession session) {
        Map<String, Object> sessionInfo = new HashMap<>();
        sessionInfo.put("sessionId", session.getId());
        sessionInfo.put("createdTime", getSessionCreatedTime(session));
        sessionInfo.put("lastActivityTime", getLastActivityTime(session));
        sessionInfo.put("maxInactiveInterval", session.getMaxInactiveInterval());
        sessionInfo.put("isNew", session.isNew());
        
        User user = getAuthenticatedUser(session);
        if (user != null) {
            sessionInfo.put("authenticated", true);
            sessionInfo.put("username", user.getUsername());
            sessionInfo.put("email", user.getEmail());
        } else {
            sessionInfo.put("authenticated", false);
        }
        
        return sessionInfo;
    }

        public LocalDateTime getSessionCreatedTime(HttpSession session) {
        return (LocalDateTime) session.getAttribute("session_created_time");
    }

    public LocalDateTime getLastActivityTime(HttpSession session) {
        return (LocalDateTime) session.getAttribute("last_activity_time");
    }

    public void updateLastActivityTime(HttpSession session) {
        session.setAttribute("last_activity_time", LocalDateTime.now());
    }

    public void invalidateSession(HttpSession session) {
        session.invalidate();
    }

    public Map<String, Object> getAllSessionAttributes(HttpSession session) {
        if (session == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> attributes = new HashMap<>();
        Enumeration<String> attributeNames = session.getAttributeNames();
        
        while (attributeNames.hasMoreElements()) {
            String attributeName = attributeNames.nextElement();
            Object attributeValue = session.getAttribute(attributeName);
            attributes.put(attributeName, attributeValue);
        }
        
        return attributes;
    }
    public Object getSessionAttribute(HttpSession session, String key) {
        if (session == null) {
            return null;
        }
        return session.getAttribute(key);
    }

    public void setSessionAttribute(HttpSession session, String key, Object value) {
        if (session != null) {
            session.setAttribute(key, value);
            
            // Update last activity time when setting attributes
            session.setAttribute("last_activity_time", LocalDateTime.now());
        }
    }

    public void removeSessionAttribute(HttpSession session, String key) {
        if (session != null) {
            session.removeAttribute(key);
           
            // Update last activity time when removing attributes
            session.setAttribute("last_activity_time", LocalDateTime.now());
        }
    }
}