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
import org.springframework.samples.petclinic.service.ApiKeyService;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for API key authentication using real HTTP calls.
 * Tests the actual authentication flow through the Spring Security filter chain.
 * Uses TestRestTemplate to make real HTTP requests to an embedded server.
 *
 * @author Spring PetClinic Team
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"spring-data-jpa", "hsqldb"})
class ApiKeyAuthenticationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApiKeyService apiKeyService;

    private String validApiKey;
    private Integer apiKeyId;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        baseUrl = "http://localhost:" + port + "/petclinic";
        // Create a valid API key for testing (using "admin" user from test data)
        var result = apiKeyService.createApiKey("Test API Key", "admin", null);
        validApiKey = result.getFullKey();
        apiKeyId = result.getApiKey().getId();
    }

    @Test
    void testSuccessfulAuthenticationWithValidApiKey() {
        // Test that a valid API key allows access to protected endpoints
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
        // Test that an invalid API key returns 401
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
        // Test that missing API key header returns 401 (no Basic Auth provided)
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
    void testFailedAuthenticationWithRevokedKey() {
        // Revoke the API key
        apiKeyService.revokeApiKey(apiKeyId);

        // Test that revoked key returns 401
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", validApiKey);
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
    void testFailedAuthenticationWithExpiredKey() {
        // Create an expired API key
        LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
        var expiredResult = apiKeyService.createApiKey("Expired Key", "admin", pastDate);
        String expiredKey = expiredResult.getFullKey();

        // Test that expired key returns 401
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
    }

    @Test
    void testBasicAuthStillWorksWhenApiKeyHeaderAbsent() {
        // Test that Basic Auth works when X-API-Key header is absent
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
        // Test that API key authentication works for various protected endpoints
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", validApiKey);
        HttpEntity<?> entity = new HttpEntity<>(headers);

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

