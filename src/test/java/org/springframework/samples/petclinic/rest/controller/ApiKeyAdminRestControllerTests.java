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

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for API key admin REST endpoints using real HTTP calls.
 * 
 * These tests verify Acceptance Criteria #2: API keys can be created, rotated, and revoked via admin REST API.
 * Tests use only public REST API endpoints - no direct service/repository access.
 * 
 * All tests use Basic Auth for authentication (admin:admin).
 * All assertions verify HTTP status codes and response bodies (public contract).
 * 
 * These tests will FAIL if admin endpoints are not implemented.
 * These tests will PASS when admin endpoints are correctly implemented.
 *
 * @author Spring PetClinic Team
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"spring-data-jpa", "h2"})
class ApiKeyAdminRestControllerTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private String baseUrl;
    private HttpHeaders adminHeaders;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/petclinic";
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        
        // Setup Basic Auth headers for admin user
        adminHeaders = new HttpHeaders();
        String credentials = Base64.getEncoder().encodeToString("admin:admin".getBytes());
        adminHeaders.set("Authorization", "Basic " + credentials);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
    }

    @Test
    void testCreateApiKeySuccess() throws Exception {
        // Acceptance Criteria #2: POST /api/admin/apikeys - Create API key
        Map<String, String> request = new HashMap<>();
        request.put("name", "Test API Key");

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertThat(responseBody.get("id")).isNotNull();
        assertThat(responseBody.get("key")).isNotNull(); // Full key only returned on creation
        assertThat(responseBody.get("keyPrefix")).isNotNull();
        assertThat(responseBody.get("name").asText()).isEqualTo("Test API Key");
        assertThat(responseBody.get("createdBy")).isNotNull();
        assertThat(responseBody.get("createdAt")).isNotNull();
    }

    @Test
    void testCreateApiKeyWithExpiration() throws Exception {
        // Acceptance Criteria #2: Create API key with optional expiresAt
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Test API Key with Expiration");
        request.put("expiresAt", LocalDateTime.now().plusDays(30).toString());

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
        assertThat(responseBody.get("expiresAt")).isNotNull();
    }

    @Test
    void testCreateApiKeyUnauthorized() throws Exception {
        // Acceptance Criteria #2: Admin endpoints require ROLE_ADMIN
        Map<String, String> request = new HashMap<>();
        request.put("name", "Test Key");

        String requestJson = objectMapper.writeValueAsString(request);
        // No authentication headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testRotateApiKeySuccess() throws Exception {
        // Acceptance Criteria #2: POST /api/admin/apikeys/{id}/rotate - Rotate key
        
        // Setup: Create an API key first
        Map<String, String> createRequest = new HashMap<>();
        createRequest.put("name", "Key to Rotate");
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        HttpEntity<String> createEntity = new HttpEntity<>(createRequestJson, adminHeaders);
        
        ResponseEntity<String> createResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            createEntity,
            String.class
        );
        
        JsonNode createResponseBody = objectMapper.readTree(createResponse.getBody());
        Integer apiKeyId = createResponseBody.get("id").asInt();

        // Execute: Rotate the key
        Map<String, Boolean> rotateRequest = new HashMap<>();
        rotateRequest.put("revokeOldKey", true);
        String rotateRequestJson = objectMapper.writeValueAsString(rotateRequest);
        HttpEntity<String> rotateEntity = new HttpEntity<>(rotateRequestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + apiKeyId + "/rotate",
            HttpMethod.POST,
            rotateEntity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode responseBody = objectMapper.readTree(response.getBody());

        // Rotated key keeps the original name; clients distinguish keys by id/keyPrefix
        assertThat(responseBody.get("id")).isNotNull();
        assertThat(responseBody.get("key")).isNotNull(); // Full key only returned on rotation
        assertThat(responseBody.get("keyPrefix")).isNotNull();
        assertThat(responseBody.get("name").asText()).isEqualTo("Key to Rotate");
    }

    @Test
    void testRotateApiKeyNotFound() throws Exception {
        // Acceptance Criteria #2: Rotate returns 404 if not found
        Map<String, Boolean> rotateRequest = new HashMap<>();
        rotateRequest.put("revokeOldKey", true);
        String rotateRequestJson = objectMapper.writeValueAsString(rotateRequest);
        HttpEntity<String> entity = new HttpEntity<>(rotateRequestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/99999/rotate",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testRotateApiKeyWithoutRevoking() throws Exception {
        // Acceptance Criteria #2: Rotate with revokeOldKey=false
        
        // Setup: Create an API key first
        Map<String, String> createRequest = new HashMap<>();
        createRequest.put("name", "Key to Rotate Without Revoking");
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        HttpEntity<String> createEntity = new HttpEntity<>(createRequestJson, adminHeaders);
        
        ResponseEntity<String> createResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            createEntity,
            String.class
        );
        
        JsonNode createResponseBody = objectMapper.readTree(createResponse.getBody());
        Integer apiKeyId = createResponseBody.get("id").asInt();
        String originalKey = createResponseBody.get("key").asText();

        // Execute: Rotate without revoking old key
        Map<String, Boolean> rotateRequest = new HashMap<>();
        rotateRequest.put("revokeOldKey", false);
        String rotateRequestJson = objectMapper.writeValueAsString(rotateRequest);
        HttpEntity<String> rotateEntity = new HttpEntity<>(rotateRequestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + apiKeyId + "/rotate",
            HttpMethod.POST,
            rotateEntity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertThat(responseBody.get("key")).isNotNull(); // New key returned
        
        // Verify old key still works (not revoked)
        HttpHeaders apiKeyHeaders = new HttpHeaders();
        apiKeyHeaders.set("X-API-Key", originalKey);
        HttpEntity<?> apiKeyEntity = new HttpEntity<>(apiKeyHeaders);
        
        ResponseEntity<String> authResponse = restTemplate.exchange(
            baseUrl + "/api/owners",
            HttpMethod.GET,
            apiKeyEntity,
            String.class
        );
        
        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.OK); // Old key still works
    }

    @Test
    void testRevokeApiKeySuccess() throws Exception {
        // Acceptance Criteria #2: POST /api/admin/apikeys/{id}/revoke - Revoke key
        
        // Setup: Create an API key first
        Map<String, String> createRequest = new HashMap<>();
        createRequest.put("name", "Key to Revoke");
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        HttpEntity<String> createEntity = new HttpEntity<>(createRequestJson, adminHeaders);
        
        ResponseEntity<String> createResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            createEntity,
            String.class
        );
        
        JsonNode createResponseBody = objectMapper.readTree(createResponse.getBody());
        Integer apiKeyId = createResponseBody.get("id").asInt();
        String originalKey = createResponseBody.get("key").asText();

        // Execute: Revoke the key
        HttpEntity<String> revokeEntity = new HttpEntity<>(adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + apiKeyId + "/revoke",
            HttpMethod.POST,
            revokeEntity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertThat(responseBody.get("id").asInt()).isEqualTo(apiKeyId);
        assertThat(responseBody.get("revokedAt")).isNotNull();
        // Full key should NOT be returned on revoke
        if (responseBody.has("key")) {
            assertThat(responseBody.get("key").isNull()).isTrue();
        }

        // Verify key is actually revoked by trying to use it
        HttpHeaders apiKeyHeaders = new HttpHeaders();
        apiKeyHeaders.set("X-API-Key", originalKey);
        HttpEntity<?> apiKeyEntity = new HttpEntity<>(apiKeyHeaders);

        ResponseEntity<String> authResponse = restTemplate.exchange(
            baseUrl + "/api/owners",
            HttpMethod.GET,
            apiKeyEntity,
            String.class
        );

        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED); // Should fail because key is revoked
    }

    @Test
    void testRevokeApiKeyNotFound() {
        // Acceptance Criteria #2: Revoke returns 404 if not found
        HttpEntity<String> entity = new HttpEntity<>(adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/99999/revoke",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testRevokeApiKeyUnauthorized() {
        // Acceptance Criteria #2: Admin endpoints require ROLE_ADMIN
        // No authentication headers
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/1/revoke",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testFullKeyOnlyReturnedOnCreationAndRotation() throws Exception {
        // Acceptance Criteria #2: Full key returned only once (immediately after creation)
        
        // Create a key - full key should be returned
        Map<String, String> createRequest = new HashMap<>();
        createRequest.put("name", "Key for Full Key Test");
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        HttpEntity<String> createEntity = new HttpEntity<>(createRequestJson, adminHeaders);

        ResponseEntity<String> createResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            createEntity,
            String.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode createResponseBody = objectMapper.readTree(createResponse.getBody());
        assertThat(createResponseBody.get("key")).isNotNull(); // Full key returned on creation
        
        Integer apiKeyId = createResponseBody.get("id").asInt();

        // Revoke the key - full key should NOT be returned
        HttpEntity<String> revokeEntity = new HttpEntity<>(adminHeaders);

        ResponseEntity<String> revokeResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + apiKeyId + "/revoke",
            HttpMethod.POST,
            revokeEntity,
            String.class
        );

        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode revokeResponseBody = objectMapper.readTree(revokeResponse.getBody());
        // Full key should NOT be returned on revoke
        if (revokeResponseBody.has("key")) {
            assertThat(revokeResponseBody.get("key").isNull()).isTrue();
        }
    }

    @Test
    void testCreateApiKeyWithEmptyName() throws Exception {
        // Validation test: Creating a key with an empty name should return 400 BAD_REQUEST
        Map<String, String> request = new HashMap<>();
        request.put("name", ""); // Empty name should trigger @NotEmpty validation

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testCreateApiKeyWithNullName() throws Exception {
        // Validation test: Creating a key with null name should return 400 BAD_REQUEST
        Map<String, Object> request = new HashMap<>();
        request.put("name", null); // Null name should trigger @NotEmpty validation

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testCreateApiKeyWithTooLongName() throws Exception {
        // Validation test: Creating a key with name > 100 characters should return 400 BAD_REQUEST
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            longName.append("a");
        }
        
        Map<String, String> request = new HashMap<>();
        request.put("name", longName.toString()); // Name > 100 chars should trigger @Size validation

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testCreateApiKeyWithValidMaxLengthName() throws Exception {
        // Edge case: Creating a key with exactly 100 characters should work
        StringBuilder maxName = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            maxName.append("a");
        }
        
        Map<String, String> request = new HashMap<>();
        request.put("name", maxName.toString()); // Exactly 100 chars should be valid

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
        assertThat(responseBody.get("name").asText()).isEqualTo(maxName.toString());
    }
}
