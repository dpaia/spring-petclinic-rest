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
import org.springframework.samples.petclinic.rest.dto.CreateApiKeyRequestDto;
import org.springframework.samples.petclinic.rest.dto.RotateApiKeyRequestDto;
import org.springframework.samples.petclinic.service.ApiKeyService;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ApiKeyAdminRestController} using real HTTP calls.
 * Tests use real database and services - no mocks.
 * Uses Basic Auth for authentication since TestRestTemplate makes real HTTP calls.
 *
 * @author Spring PetClinic Team
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"spring-data-jpa", "hsqldb"})
class ApiKeyAdminRestControllerTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApiKeyService apiKeyService;

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

    private HttpHeaders createNonAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        // Use a user that exists but doesn't have ADMIN role
        // Based on test data, we can use a user without ADMIN role
        // For unauthorized tests, we'll use invalid credentials to get 401, or
        // we can test with a user that exists but lacks ADMIN role
        // Since test data may vary, we'll test with invalid credentials to ensure 401
        String credentials = Base64.getEncoder().encodeToString("nonexistent:user".getBytes());
        headers.set("Authorization", "Basic " + credentials);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    @Test
    void testCreateApiKeySuccess() throws Exception {
        CreateApiKeyRequestDto request = new CreateApiKeyRequestDto();
        request.setName("Test API Key");

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
        assertThat(responseBody.get("createdBy").asText()).isEqualTo("admin");
        assertThat(responseBody.get("createdAt")).isNotNull();
    }

    @Test
    void testCreateApiKeyWithExpiration() throws Exception {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);
        CreateApiKeyRequestDto request = new CreateApiKeyRequestDto();
        request.setName("Test API Key with Expiration");
        request.setExpiresAt(expiresAt);

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
    void testCreateApiKeyValidationError() throws Exception {
        CreateApiKeyRequestDto request = new CreateApiKeyRequestDto();
        request.setName(""); // Empty name should fail @NotEmpty validation

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
    void testCreateApiKeyUnauthorized() throws Exception {
        CreateApiKeyRequestDto request = new CreateApiKeyRequestDto();
        request.setName("Test Key");

        String requestJson = objectMapper.writeValueAsString(request);
        // Test without authentication - should return 401
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
        // Setup: Create an API key first
        var createResult = apiKeyService.createApiKey("Key to Rotate", "admin", null);
        Integer apiKeyId = createResult.getApiKey().getId();

        // Execute: Rotate the key
        RotateApiKeyRequestDto request = new RotateApiKeyRequestDto();
        request.setRevokeOldKey(true);

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + apiKeyId + "/rotate",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertThat(responseBody.get("id")).isNotNull();
        assertThat(responseBody.get("key")).isNotNull(); // Full key only returned on rotation
        assertThat(responseBody.get("keyPrefix")).isNotNull();
        assertThat(responseBody.get("name").asText()).isEqualTo("Key to Rotate (rotated)");
    }

    @Test
    void testRotateApiKeyNotFound() throws Exception {
        RotateApiKeyRequestDto request = new RotateApiKeyRequestDto();
        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

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
        // Setup: Create an API key first
        var createResult = apiKeyService.createApiKey("Key to Rotate Without Revoking", "admin", null);
        Integer apiKeyId = createResult.getApiKey().getId();
        String originalKey = createResult.getFullKey();

        // Execute: Rotate without revoking old key
        RotateApiKeyRequestDto request = new RotateApiKeyRequestDto();
        request.setRevokeOldKey(false);

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + apiKeyId + "/rotate",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertThat(responseBody.get("key")).isNotNull(); // New key returned
    }

    @Test
    void testRevokeApiKeySuccess() throws Exception {
        // Setup: Create an API key first
        var createResult = apiKeyService.createApiKey("Key to Revoke", "admin", null);
        Integer apiKeyId = createResult.getApiKey().getId();
        String originalKey = createResult.getFullKey();

        // Execute: Revoke the key
        HttpEntity<String> entity = new HttpEntity<>(adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + apiKeyId + "/revoke",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertThat(responseBody.get("id").asInt()).isEqualTo(apiKeyId);
        assertThat(responseBody.get("revokedAt")).isNotNull();
        // Full key should NOT be returned on revoke - check if field is missing or null
        if (responseBody.has("key")) {
            assertThat(responseBody.get("key").isNull()).isTrue();
        }

        // Verify key is actually revoked by trying to use it with real HTTP call
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
    void testRevokeApiKeyNotFound() throws Exception {
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
    void testRevokeApiKeyUnauthorized() throws Exception {
        // Setup: Create an API key first
        var createResult = apiKeyService.createApiKey("Key for Unauthorized Test", "admin", null);
        Integer apiKeyId = createResult.getApiKey().getId();

        // Execute without authentication - should return 401
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + apiKeyId + "/revoke",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testFullKeyOnlyReturnedOnCreationAndRotation() throws Exception {
        // Create a key - full key should be returned
        CreateApiKeyRequestDto createRequest = new CreateApiKeyRequestDto();
        createRequest.setName("Key for Full Key Test");

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
        // Full key should NOT be returned on revoke - check if field is missing or null
        if (revokeResponseBody.has("key")) {
            assertThat(revokeResponseBody.get("key").isNull()).isTrue();
        }
    }
}
