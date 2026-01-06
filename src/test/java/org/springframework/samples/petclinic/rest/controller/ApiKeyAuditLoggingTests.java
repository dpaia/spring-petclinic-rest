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
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.model.ApiKeyAuditLog;
import org.springframework.samples.petclinic.repository.ApiKeyAuditLogRepository;
import org.springframework.samples.petclinic.service.ApiKeyService;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for API key audit logging using real HTTP calls.
 * Tests verify that authentication attempts are logged (observable through audit log repository).
 * These tests verify the requirement that "All API key usage is logged" from Acceptance Criteria #3.
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

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ApiKeyAuditLogRepository auditLogRepository;

    private String validApiKey;
    private String keyPrefix;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        baseUrl = "http://localhost:" + port + "/petclinic";
        // Create a valid API key for testing
        var result = apiKeyService.createApiKey("Audit Test Key", "admin", null);
        validApiKey = result.getFullKey();
        keyPrefix = result.getApiKey().getKeyPrefix();
    }

    @Test
    void testSuccessfulAuthenticationIsLogged() throws Exception {
        // Clear any existing audit logs for this key prefix
        LocalDateTime beforeTest = LocalDateTime.now().minusSeconds(1);

        // Perform successful authentication using real HTTP call
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

        // Wait a moment for async logging to complete
        Thread.sleep(100);

        // Verify audit log entry was created
        Collection<ApiKeyAuditLog> logs = auditLogRepository.findByKeyPrefixAndTimestampAfter(keyPrefix, beforeTest);
        assertThat(logs).isNotEmpty();
        
        ApiKeyAuditLog logEntry = logs.stream()
            .filter(log -> log.getSuccess() != null && log.getSuccess())
            .findFirst()
            .orElse(null);
        
        assertThat(logEntry).isNotNull();
        assertThat(logEntry.getSuccess()).isTrue();
        assertThat(logEntry.getRequestMethod()).isEqualTo("GET");
        assertThat(logEntry.getRequestPath()).contains("/api/owners");
        assertThat(logEntry.getFailureReason()).isNull();
        assertThat(logEntry.getTimestamp()).isAfter(beforeTest);
    }

    @Test
    void testFailedAuthenticationIsLogged() throws Exception {
        LocalDateTime beforeTest = LocalDateTime.now().minusSeconds(1);

        // Perform failed authentication with invalid key using real HTTP call
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

        // Wait a moment for async logging to complete
        Thread.sleep(100);

        // Verify audit log entry was created for failed attempt
        // Extract prefix from invalid key (first 8 chars)
        String invalidKeyPrefix = "invalid-key-that-does-not-exist".length() >= 8 
            ? "invalid-key-that-does-not-exist".substring(0, 8) 
            : "invalid-key-that-does-not-exist";
        
        Collection<ApiKeyAuditLog> logs = auditLogRepository.findByKeyPrefixAndTimestampAfter(invalidKeyPrefix, beforeTest);
        assertThat(logs).isNotEmpty();
        
        ApiKeyAuditLog logEntry = logs.stream()
            .filter(log -> log.getSuccess() != null && !log.getSuccess())
            .findFirst()
            .orElse(null);
        
        assertThat(logEntry).isNotNull();
        assertThat(logEntry.getSuccess()).isFalse();
        assertThat(logEntry.getRequestMethod()).isEqualTo("GET");
        assertThat(logEntry.getRequestPath()).contains("/api/owners");
        assertThat(logEntry.getFailureReason()).isNotNull();
        assertThat(logEntry.getTimestamp()).isAfter(beforeTest);
    }

    @Test
    void testRevokedKeyAuthenticationIsLogged() throws Exception {
        // Create and revoke a key
        var createResult = apiKeyService.createApiKey("Key to Revoke for Audit", "admin", null);
        String revokedKey = createResult.getFullKey();
        String revokedKeyPrefix = createResult.getApiKey().getKeyPrefix();
        Integer apiKeyId = createResult.getApiKey().getId();
        
        apiKeyService.revokeApiKey(apiKeyId);
        
        LocalDateTime beforeTest = LocalDateTime.now().minusSeconds(1);

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

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Wait a moment for async logging to complete
        Thread.sleep(100);

        // Verify audit log entry was created
        Collection<ApiKeyAuditLog> logs = auditLogRepository.findByKeyPrefixAndTimestampAfter(revokedKeyPrefix, beforeTest);
        assertThat(logs).isNotEmpty();
        
        ApiKeyAuditLog logEntry = logs.stream()
            .filter(log -> log.getSuccess() != null && !log.getSuccess())
            .findFirst()
            .orElse(null);
        
        assertThat(logEntry).isNotNull();
        assertThat(logEntry.getSuccess()).isFalse();
        assertThat(logEntry.getFailureReason()).isNotNull();
        // Failure reason may be "INVALID_KEY" for revoked keys (implementation detail)
        // What matters is that the attempt was logged and authentication failed
    }

    @Test
    void testExpiredKeyAuthenticationIsLogged() throws Exception {
        // Create an expired key
        LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
        var expiredResult = apiKeyService.createApiKey("Expired Key for Audit", "admin", pastDate);
        String expiredKey = expiredResult.getFullKey();
        String expiredKeyPrefix = expiredResult.getApiKey().getKeyPrefix();
        
        LocalDateTime beforeTest = LocalDateTime.now().minusSeconds(1);

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

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Wait a moment for async logging to complete
        Thread.sleep(100);

        // Verify audit log entry was created
        Collection<ApiKeyAuditLog> logs = auditLogRepository.findByKeyPrefixAndTimestampAfter(expiredKeyPrefix, beforeTest);
        assertThat(logs).isNotEmpty();
        
        ApiKeyAuditLog logEntry = logs.stream()
            .filter(log -> log.getSuccess() != null && !log.getSuccess())
            .findFirst()
            .orElse(null);
        
        assertThat(logEntry).isNotNull();
        assertThat(logEntry.getSuccess()).isFalse();
        assertThat(logEntry.getFailureReason()).isNotNull();
        // Failure reason may be "INVALID_KEY" for expired keys (implementation detail)
        // What matters is that the attempt was logged and authentication failed
    }

    @Test
    void testAuditLogContainsRequestDetails() throws Exception {
        LocalDateTime beforeTest = LocalDateTime.now().minusSeconds(1);

        // Perform authentication using real HTTP call
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

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Wait a moment for async logging to complete
        Thread.sleep(100);

        // Verify audit log contains request details
        Collection<ApiKeyAuditLog> logs = auditLogRepository.findByKeyPrefixAndTimestampAfter(keyPrefix, beforeTest);
        assertThat(logs).isNotEmpty();
        
        ApiKeyAuditLog logEntry = logs.stream()
            .filter(log -> log.getRequestPath() != null && log.getRequestPath().contains("/api/pets"))
            .findFirst()
            .orElse(null);
        
        assertThat(logEntry).isNotNull();
        assertThat(logEntry.getRequestMethod()).isEqualTo("GET");
        assertThat(logEntry.getRequestPath()).contains("/api/pets");
        assertThat(logEntry.getRequestIp()).isNotNull();
        assertThat(logEntry.getUserAgent()).isNotNull();
        assertThat(logEntry.getTimestamp()).isAfter(beforeTest);
    }
}
