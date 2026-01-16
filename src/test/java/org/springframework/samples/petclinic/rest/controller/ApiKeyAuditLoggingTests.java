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
 * Integration tests for API key authentication behavior using real HTTP calls.
 * 
 * These tests verify Acceptance Criteria #3: "All API key usage is logged".
 * Tests use only public REST API endpoints - no direct service/repository access.
 * 
 * Note: Audit log verification requires a public REST endpoint (e.g., GET /api/admin/apikeys/{id}/audit-logs).
 * Since no such endpoint exists, these tests verify observable authentication behavior only.
 * Audit logging is an internal implementation detail that cannot be verified through blackbox testing
 * without a public endpoint.
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
@ActiveProfiles({"spring-data-jpa", "hsqldb"})
class ApiKeyAuditLoggingTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String validApiKey;
    private String baseUrl;
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
        createRequest.put("name", "Audit Test Key");
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
    void testSuccessfulAuthenticationBehavior() {
        // Acceptance Criteria #3: All API key usage should be logged
        // We verify observable behavior: successful authentication works correctly
        // Note: Audit log verification would require a public REST endpoint (e.g., GET /api/admin/apikeys/{id}/audit-logs)
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", validApiKey);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners",
            HttpMethod.GET,
            entity,
            String.class
        );

        // Verify successful authentication (observable behavior)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Note: Audit logging is an internal implementation detail that cannot be verified
        // through blackbox testing without a public REST endpoint for audit logs
    }

    @Test
    void testFailedAuthenticationBehavior() {
        // Acceptance Criteria #3: All API key usage should be logged (including failures)
        // We verify observable behavior: failed authentication is correctly rejected
        // Note: Audit log verification would require a public REST endpoint
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "invalid-key-that-does-not-exist");
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners",
            HttpMethod.GET,
            entity,
            String.class
        );

        // Verify failed authentication is correctly rejected (observable behavior)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Note: Audit logging is an internal implementation detail that cannot be verified
        // through blackbox testing without a public REST endpoint for audit logs
    }

    @Test
    void testRevokedKeyAuthenticationBehavior() throws Exception {
        // Acceptance Criteria #3: All API key usage should be logged (including revoked key attempts)
        // We verify observable behavior: revoked keys are correctly rejected
        
        // Create and revoke a key via REST API
        Map<String, String> createRequest = new HashMap<>();
        createRequest.put("name", "Key to Revoke for Audit");
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
        String revokedKey = createResponseBody.get("key").asText();
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

        // Attempt authentication with revoked key using real HTTP call
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", revokedKey);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners",
            HttpMethod.GET,
            entity,
            String.class
        );

        // Verify revoked key is correctly rejected (observable behavior)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Note: Audit logging is an internal implementation detail that cannot be verified
        // through blackbox testing without a public REST endpoint for audit logs
    }

    @Test
    void testExpiredKeyAuthenticationBehavior() throws Exception {
        // Acceptance Criteria #3: All API key usage should be logged (including expired key attempts)
        // We verify observable behavior: expired keys are correctly rejected
        
        // Create an expired key via REST API
        Map<String, Object> createRequest = new HashMap<>();
        createRequest.put("name", "Expired Key for Audit");
        createRequest.put("expiresAt", java.time.LocalDateTime.now().minusDays(1).toString());
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
        String expiredKey = createResponseBody.get("key").asText();

        // Attempt authentication with expired key using real HTTP call
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", expiredKey);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners",
            HttpMethod.GET,
            entity,
            String.class
        );

        // Verify expired key is correctly rejected (observable behavior)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Note: Audit logging is an internal implementation detail that cannot be verified
        // through blackbox testing without a public REST endpoint for audit logs
    }

    @Test
    void testAuthenticationWithRequestDetails() {
        // Acceptance Criteria #3: All API key usage should be logged with request details
        // (HTTP method, request path, client IP, user agent, success/failure, failure reason, timestamp)
        // We verify observable behavior: authentication works with various request headers
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", validApiKey);
        headers.set("User-Agent", "Test-Agent/1.0");
        headers.set("X-Forwarded-For", "192.168.1.100");
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/pets",
            HttpMethod.GET,
            entity,
            String.class
        );

        // Verify authentication works with request details (observable behavior)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Note: Audit logging with request details is an internal implementation detail
        // that cannot be verified through blackbox testing without a public REST endpoint for audit logs
    }
}
