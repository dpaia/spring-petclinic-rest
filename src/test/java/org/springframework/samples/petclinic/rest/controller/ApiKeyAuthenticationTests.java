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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.model.ApiKey;
import org.springframework.samples.petclinic.service.ApiKeyService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for API key authentication.
 * Tests the actual authentication flow through the Spring Security filter chain.
 *
 * @author Spring PetClinic Team
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"spring-data-jpa", "hsqldb"})
class ApiKeyAuthenticationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyService apiKeyService;

    private String validApiKey;
    private Integer apiKeyId;

    @BeforeEach
    void setUp() throws Exception {
        // Create a valid API key for testing (using "admin" user from test data)
        var result = apiKeyService.createApiKey("Test API Key", "admin", null);
        validApiKey = result.getFullKey();
        apiKeyId = result.getApiKey().getId();
    }

    @Test
    void testSuccessfulAuthenticationWithValidApiKey() throws Exception {
        // Test that a valid API key allows access to protected endpoints
        this.mockMvc.perform(get("/api/owners")
                .header("X-API-Key", validApiKey)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    void testFailedAuthenticationWithInvalidApiKey() throws Exception {
        // Test that an invalid API key returns 401
        this.mockMvc.perform(get("/api/owners")
                .header("X-API-Key", "invalid-key-that-does-not-exist")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testFailedAuthenticationWithMissingHeader() throws Exception {
        // Test that missing API key header returns 401 (no Basic Auth provided)
        this.mockMvc.perform(get("/api/owners")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testFailedAuthenticationWithRevokedKey() throws Exception {
        // Revoke the API key
        apiKeyService.revokeApiKey(apiKeyId);

        // Test that revoked key returns 401
        this.mockMvc.perform(get("/api/owners")
                .header("X-API-Key", validApiKey)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testFailedAuthenticationWithExpiredKey() throws Exception {
        // Create an expired API key
        LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
        var expiredResult = apiKeyService.createApiKey("Expired Key", "admin", pastDate);
        String expiredKey = expiredResult.getFullKey();

        // Test that expired key returns 401
        this.mockMvc.perform(get("/api/owners")
                .header("X-API-Key", expiredKey)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testBasicAuthStillWorksWhenApiKeyHeaderAbsent() throws Exception {
        // Test that Basic Auth works when X-API-Key header is absent
        String credentials = Base64.getEncoder().encodeToString("admin:admin".getBytes());

        this.mockMvc.perform(get("/api/owners")
                .header("Authorization", "Basic " + credentials)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    void testApiKeyWorksForAllProtectedEndpoints() throws Exception {
        // Test that API key authentication works for various protected endpoints
        this.mockMvc.perform(get("/api/pets")
                .header("X-API-Key", validApiKey)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        this.mockMvc.perform(get("/api/vets")
                .header("X-API-Key", validApiKey)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }
}

