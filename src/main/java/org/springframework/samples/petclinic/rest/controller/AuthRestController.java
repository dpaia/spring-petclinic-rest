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
@RequestMapping("/api/auth")
@CrossOrigin(exposedHeaders = "errors, content-type")
public class AuthRestController {

    @Autowired
    private SessionManagementService sessionManagementService;

    /**
     * Initiate OAuth2 login - returns login URL or redirects to Google OAuth2 authorization
     */
    @GetMapping("/login")
    public ResponseEntity<Map<String, Object>> initiateLogin(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
      HttpSession session = request.getSession(false);
      User authenticatedUser = null;

        if (session != null) {
            authenticatedUser = sessionManagementService.getAuthenticatedUser(session);
        }

        if (authenticatedUser != null) {
            response.put("authenticated", true);
            response.put("message", "User already authenticated");
            response.put("user", createUserResponse(authenticatedUser));
            return ResponseEntity.ok(response);
        }
        
        // Return login URL for OAuth2 flow
        response.put("authenticated", false);
        response.put("message", "Redirect to OAuth2 provider");
        response.put("loginUrl", "/oauth2/authorization/google");
        return ResponseEntity.ok(response);
    }

    /**
     * Get current authentication status and user information
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAuthenticationStatus(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        
        User authenticatedUser = sessionManagementService.getAuthenticatedUser(session);
        
        if (authenticatedUser != null) {
            response.put("authenticated", true);
            response.put("user", createUserResponse(authenticatedUser));
            response.put("sessionInfo", sessionManagementService.getSessionInfo(session));
        } else {
            response.put("authenticated", false);
            response.put("message", "User not authenticated");
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Handle OAuth2 login success (called by success handler)
     */
    @GetMapping("/success")
    public ResponseEntity<Map<String, Object>> loginSuccess(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        
        User authenticatedUser = sessionManagementService.getAuthenticatedUser(session);
        
        if (authenticatedUser != null) {
            response.put("success", true);
            response.put("message", "Authentication successful");
            response.put("user", createUserResponse(authenticatedUser));
            response.put("redirectUrl", "/api/session/info");
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Authentication failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    /**
     * Handle OAuth2 login failure
     */
    @GetMapping("/error")
    public ResponseEntity<Map<String, Object>> loginError(@RequestParam(required = false) String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Authentication failed");
        if (error != null) {
            response.put("error", error);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
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