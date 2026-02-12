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
 * Integration tests for API key authentication using real HTTP calls.
 * 
 * These tests verify Acceptance Criteria #1: API key authentication via X-API-Key header.
 * Tests use only public REST API endpoints - no direct service/repository access.
 * 
 * Test setup: Creates API keys via POST /api/admin/apikeys (public endpoint)
 * Test assertions: Verify HTTP status codes and responses (public contract)
 * 
 * These tests will FAIL if API key authentication is not implemented.
 * These tests will PASS when API key authentication is correctly implemented.
 *
 * @author Spring PetClinic Team
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"spring-data-jpa", "h2"})
class ApiKeyAuthenticationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;
    private String validApiKey;
    private ObjectMapper objectMapper;
    private HttpHeaders adminHeaders;

    @BeforeEach
    void setUp() throws Exception {
        baseUrl = "http://localhost:" + port + "/petclinic";
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        // Setup Basic Auth for admin user (to create API keys)
        adminHeaders = new HttpHeaders();
        String credentials = Base64.getEncoder().encodeToString("admin:admin".getBytes());
        adminHeaders.set("Authorization", "Basic " + credentials);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        
        // Create a valid API key via REST API (public endpoint)
        Map<String, String> createRequest = new HashMap<>();
        createRequest.put("name", "Test API Key");
        String requestJson = objectMapper.writeValueAsString(createRequest);
        HttpEntity<String> createEntity = new HttpEntity<>(requestJson, adminHeaders);
        
        ResponseEntity<String> createResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            createEntity,
            String.class
        );
        
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode responseBody = objectMapper.readTree(createResponse.getBody());
        validApiKey = responseBody.get("key").asText(); // Full key only returned on creation
    }

    @Test
    void testSuccessfulAuthenticationWithValidApiKey() {
        // Acceptance Criteria #1: Requests with valid API key in header can access protected endpoints
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", validApiKey);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners",
            HttpMethod.GET,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testFailedAuthenticationWithInvalidApiKey() {
        // Acceptance Criteria #1: Requests with invalid API key return 401 Unauthorized
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "invalid-key-that-does-not-exist");
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners",
            HttpMethod.GET,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testFailedAuthenticationWithMissingHeader() {
        // Acceptance Criteria #1: Requests with missing API key return 401 (when no Basic Auth)
        HttpEntity<?> entity = new HttpEntity<>(new HttpHeaders());

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners",
            HttpMethod.GET,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testFailedAuthenticationWithRevokedKey() throws Exception {
        // Acceptance Criteria #2: Expired and revoked keys must be rejected during authentication
        
        // Setup: Create and revoke an API key via REST API
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
        String revokedApiKey = createResponseBody.get("key").asText();
        Integer apiKeyId = createResponseBody.get("id").asInt();
        
        // Revoke the key via REST API
        HttpEntity<String> revokeEntity = new HttpEntity<>(adminHeaders);
        ResponseEntity<String> revokeResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + apiKeyId + "/revoke",
            HttpMethod.POST,
            revokeEntity,
            String.class
        );
        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Test: Revoked key should return 401
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", revokedApiKey);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners",
            HttpMethod.GET,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testFailedAuthenticationWithExpiredKey() throws Exception {
        // Acceptance Criteria #2: Expired and revoked keys must be rejected during authentication
        
        // Setup: Create an expired API key via REST API
        Map<String, Object> createRequest = new HashMap<>();
        createRequest.put("name", "Expired Key");
        // Set expiration to past date
        createRequest.put("expiresAt", LocalDateTime.now().minusDays(1).toString());
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        HttpEntity<String> createEntity = new HttpEntity<>(createRequestJson, adminHeaders);
        
        ResponseEntity<String> createResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            createEntity,
            String.class
        );
        
        JsonNode createResponseBody = objectMapper.readTree(createResponse.getBody());
        String expiredApiKey = createResponseBody.get("key").asText();
        
        // Test: Expired key should return 401
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", expiredApiKey);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners",
            HttpMethod.GET,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testBasicAuthStillWorksWhenApiKeyHeaderAbsent() {
        // Acceptance Criteria #1: If X-API-Key header is absent, allow other authentication methods (Basic Auth) to proceed
        String credentials = Base64.getEncoder().encodeToString("admin:admin".getBytes());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + credentials);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners",
            HttpMethod.GET,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testApiKeyWorksForAllProtectedEndpoints() {
        // Acceptance Criteria #1: API key authentication must work for all endpoints that currently require Basic Authentication
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", validApiKey);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        // Test various protected endpoints
        ResponseEntity<String> petsResponse = restTemplate.exchange(
            baseUrl + "/api/pets",
            HttpMethod.GET,
            entity,
            String.class
        );
        assertThat(petsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> vetsResponse = restTemplate.exchange(
            baseUrl + "/api/vets",
            HttpMethod.GET,
            entity,
            String.class
        );
        assertThat(vetsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
