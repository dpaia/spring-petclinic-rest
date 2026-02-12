/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.rest.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security tests for API key authentication and authorization.
 * 
 * These tests verify that API keys have limited machine-to-machine access
 * and cannot perform administrative operations, following the principle of least privilege.
 * 
 * CRITICAL: These tests verify that API keys CANNOT access admin endpoints,
 * which is essential for security compliance.
 *
 * @author Spring PetClinic Team
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"spring-data-jpa", "h2"})
class ApiKeySecurityTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private String baseUrl;
    private HttpHeaders adminHeaders;
    private HttpHeaders apiKeyHeaders;
    private String testApiKey;

    @BeforeEach
    void setUp() throws Exception {
        baseUrl = "http://localhost:" + port + "/petclinic";
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        
        // Setup Basic Auth headers for admin user
        adminHeaders = new HttpHeaders();
        String credentials = Base64.getEncoder().encodeToString("admin:admin".getBytes());
        adminHeaders.set("Authorization", "Basic " + credentials);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);

        // Create an API key for testing
        createTestApiKey();
    }

    private void createTestApiKey() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("name", "Test Security Key");

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        testApiKey = responseBody.get("key").asText();

        // Setup API key headers
        apiKeyHeaders = new HttpHeaders();
        apiKeyHeaders.set("X-API-Key", testApiKey);
        apiKeyHeaders.setContentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void testApiKeyCannotCreateOtherApiKeys() throws Exception {
        // CRITICAL: API keys should NOT be able to create other API keys
        Map<String, String> request = new HashMap<>();
        request.put("name", "Unauthorized Key Creation Attempt");

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, apiKeyHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            entity,
            String.class
        );

        // Should return 403 FORBIDDEN - API keys cannot access admin endpoints
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void testApiKeyCannotRotateKeys() throws Exception {
        // CRITICAL: API keys should NOT be able to rotate other keys
        HttpEntity<String> entity = new HttpEntity<>(apiKeyHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/1/rotate",
            HttpMethod.POST,
            entity,
            String.class
        );

        // Should return 403 FORBIDDEN - API keys cannot access admin endpoints
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void testApiKeyCannotRevokeKeys() throws Exception {
        // CRITICAL: API keys should NOT be able to revoke other keys
        HttpEntity<String> entity = new HttpEntity<>(apiKeyHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/1/revoke",
            HttpMethod.POST,
            entity,
            String.class
        );

        // Should return 403 FORBIDDEN - API keys cannot access admin endpoints
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void testApiKeyCanAccessPublicReadOnlyEndpoints() throws Exception {
        // API keys should be able to access public read-only endpoints
        String[] publicEndpoints = {
            "/api/pettypes",
            "/api/vets",
            "/api/specialties"
        };

        for (String endpoint : publicEndpoints) {
            HttpEntity<String> entity = new HttpEntity<>(apiKeyHeaders);

            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + endpoint,
                HttpMethod.GET,
                entity,
                String.class
            );

            // Should return 200 OK or other successful status (not 403 FORBIDDEN)
            assertThat(response.getStatusCode())
                .as("API key should be able to access public endpoint: " + endpoint)
                .isNotEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void testAdminCanStillAccessAdminEndpoints() throws Exception {
        // Verify that admin users can still access admin endpoints (sanity check)
        Map<String, String> createRequest = new HashMap<>();
        createRequest.put("name", "Admin Access Sanity Key");
        String requestJson = objectMapper.writeValueAsString(createRequest);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            entity,
            String.class
        );

        // Admin should still be able to access admin endpoints
        assertThat(response.getStatusCode())
            .as("Admin user should still be able to access admin endpoints")
            .isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void testApiKeyWithInvalidKeyReturnsUnauthorized() throws Exception {
        // Test with invalid API key
        HttpHeaders invalidApiKeyHeaders = new HttpHeaders();
        invalidApiKeyHeaders.set("X-API-Key", "invalid-key-12345");
        invalidApiKeyHeaders.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(invalidApiKeyHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/pettypes",
            HttpMethod.GET,
            entity,
            String.class
        );

        // Should return 401 UNAUTHORIZED for invalid key
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testApiKeyAuthenticationHasLimitedRole() throws Exception {
        // This test verifies that API keys get ROLE_API_CLIENT instead of admin roles
        // We do this by testing access patterns - API keys should have limited access
        
        // API key should NOT be able to create users (admin operation)
        Map<String, String> userRequest = new HashMap<>();
        userRequest.put("username", "testuser");
        userRequest.put("password", "password");

        String userRequestJson = objectMapper.writeValueAsString(userRequest);
        HttpEntity<String> entity = new HttpEntity<>(userRequestJson, apiKeyHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/users",
            HttpMethod.POST,
            entity,
            String.class
        );

        // Should return 403 FORBIDDEN - API keys don't have admin privileges
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
