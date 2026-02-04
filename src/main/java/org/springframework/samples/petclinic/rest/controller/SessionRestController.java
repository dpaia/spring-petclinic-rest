package org.springframework.samples.petclinic.rest.controller;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.model.User;
import org.springframework.samples.petclinic.service.SessionManagementService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/session")
@CrossOrigin(exposedHeaders = "errors, content-type")
public class SessionRestController {

    @Autowired
    private SessionManagementService sessionManagementService;

    /**
     * Get session information
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getSessionInfo(HttpSession session) {
        
        Map<String, Object> response = new HashMap<>();
        
        User authenticatedUser = sessionManagementService.getAuthenticatedUser(session);
        if (authenticatedUser == null) {
            response.put("error", "No active session found");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        sessionManagementService.updateLastActivityTime(session);
        response.put("sessionInfo", sessionManagementService.getSessionInfo(session));
        return ResponseEntity.ok(response);
    }

    /**
     * Get authenticated user information
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getAuthenticatedUser(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        
        User authenticatedUser = sessionManagementService.getAuthenticatedUser(session);
        if (authenticatedUser == null) {
            response.put("error", "User not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        sessionManagementService.updateLastActivityTime(session);
        response.put("user", createUserResponse(authenticatedUser));
        return ResponseEntity.ok(response);
    }

    /**
     * Get all session attributes
     */
    @GetMapping("/attributes")
    public ResponseEntity<Map<String, Object>> getAllSessionAttributes(HttpSession session) {
        
        Map<String, Object> response = new HashMap<>();
        
        User authenticatedUser = sessionManagementService.getAuthenticatedUser(session);
        if (authenticatedUser == null) {
            response.put("error", "User not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        Map<String, Object> attributes = sessionManagementService.getAllSessionAttributes(session);
        return ResponseEntity.ok(attributes);
    }

    /**
     * Get a specific session attribute by key
     */
    @GetMapping("/attributes/{key}")
    public ResponseEntity<Map<String, Object>> getSessionAttribute(@PathVariable String key, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        
        if (session == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "No active session");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        Object value = sessionManagementService.getSessionAttribute(session, key);
        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("value", value);
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/attributes/{key}")
    public ResponseEntity<Map<String, Object>> setSessionAttribute(
            @PathVariable String key, @RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        
        if (session == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "No active session");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        // Prevent overwriting system attributes
        if (isSystemAttribute(key)) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Cannot modify system attribute: " + key);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        
        Object value = requestBody.get("value");
        sessionManagementService.setSessionAttribute(session, key, value);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Session attribute set successfully");
        response.put("key", key);
        response.put("value", value);
        
        return ResponseEntity.ok(response);
    }

      @DeleteMapping("/attributes/{key}")
    public ResponseEntity<Map<String, Object>> removeSessionAttribute(
            @PathVariable String key, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        
        if (session == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "No active session");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        // Prevent removing system attributes
        if (isSystemAttribute(key)) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Cannot remove system attribute: " + key);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        
        sessionManagementService.removeSessionAttribute(session, key);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Session attribute removed successfully");
        response.put("key", key);
        
        return ResponseEntity.ok(response);
    }

    private boolean isSystemAttribute(String key) {
        return key.equals("authenticated_user") || 
               key.equals("session_created_time") || 
               key.equals("last_activity_time") ||
               key.startsWith("SPRING_SECURITY_");
    }

    private Map<String, Object> createUserResponse(User user) {
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("username", user.getUsername());
        userResponse.put("email", user.getEmail());
        userResponse.put("firstName", user.getFirstName());
        userResponse.put("lastName", user.getLastName());
        userResponse.put("pictureUrl", user.getPictureUrl());
        userResponse.put("enabled", user.getEnabled());
        userResponse.put("oauthProvider", user.getOauthProvider());
        
        // Include roles
        if (user.getRoles() != null) {
            userResponse.put("roles", user.getRoles().stream()
                .map(role -> role.getName())
                .toList());
        }
        
        return userResponse;
    }
}