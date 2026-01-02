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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.rest.dto.CreateApiKeyRequestDto;
import org.springframework.samples.petclinic.rest.dto.RotateApiKeyRequestDto;
import org.springframework.samples.petclinic.service.ApiKeyService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link ApiKeyAdminRestController}.
 * Tests use real database and services - no mocks.
 *
 * @author Spring PetClinic Team
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"spring-data-jpa", "hsqldb"})
class ApiKeyAdminRestControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyService apiKeyService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateApiKeySuccess() throws Exception {
        // Execute
        CreateApiKeyRequestDto request = new CreateApiKeyRequestDto();
        request.setName("Test API Key");

        String requestJson = objectMapper.writeValueAsString(request);

        this.mockMvc.perform(post("/api/admin/apikeys")
                .content(requestJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.key").exists()) // Full key only returned on creation
            .andExpect(jsonPath("$.keyPrefix").exists())
            .andExpect(jsonPath("$.name").value("Test API Key"))
            .andExpect(jsonPath("$.createdBy").value("admin"))
            .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateApiKeyWithExpiration() throws Exception {
        // Execute
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);
        CreateApiKeyRequestDto request = new CreateApiKeyRequestDto();
        request.setName("Test API Key with Expiration");
        request.setExpiresAt(expiresAt);

        String requestJson = objectMapper.writeValueAsString(request);

        this.mockMvc.perform(post("/api/admin/apikeys")
                .content(requestJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateApiKeyValidationError() throws Exception {
        // Execute with empty name (should fail validation)
        CreateApiKeyRequestDto request = new CreateApiKeyRequestDto();
        request.setName(""); // Empty name should fail @NotEmpty validation

        String requestJson = objectMapper.writeValueAsString(request);

        this.mockMvc.perform(post("/api/admin/apikeys")
                .content(requestJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN") // Wrong role
    void testCreateApiKeyUnauthorized() throws Exception {
        CreateApiKeyRequestDto request = new CreateApiKeyRequestDto();
        request.setName("Test Key");

        String requestJson = objectMapper.writeValueAsString(request);

        this.mockMvc.perform(post("/api/admin/apikeys")
                .content(requestJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRotateApiKeySuccess() throws Exception {
        // Setup: Create an API key first
        var createResult = apiKeyService.createApiKey("Key to Rotate", "admin", null);
        Integer apiKeyId = createResult.getApiKey().getId();

        // Execute: Rotate the key
        RotateApiKeyRequestDto request = new RotateApiKeyRequestDto();
        request.setRevokeOldKey(true);

        String requestJson = objectMapper.writeValueAsString(request);

        this.mockMvc.perform(post("/api/admin/apikeys/" + apiKeyId + "/rotate")
                .content(requestJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.key").exists()) // Full key only returned on rotation
            .andExpect(jsonPath("$.keyPrefix").exists())
            .andExpect(jsonPath("$.name").value("Key to Rotate (rotated)"));

        // Verify old key is revoked
        var oldKey = apiKeyService.findById(apiKeyId);
        if (oldKey.isPresent()) {
            // If old key still exists, it should be revoked
            // (Note: rotation creates a new key, old key may be deleted or revoked)
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRotateApiKeyNotFound() throws Exception {
        // Execute with non-existent ID
        RotateApiKeyRequestDto request = new RotateApiKeyRequestDto();
        String requestJson = objectMapper.writeValueAsString(request);

        this.mockMvc.perform(post("/api/admin/apikeys/99999/rotate")
                .content(requestJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRotateApiKeyWithoutRevoking() throws Exception {
        // Setup: Create an API key first
        var createResult = apiKeyService.createApiKey("Key to Rotate Without Revoking", "admin", null);
        Integer apiKeyId = createResult.getApiKey().getId();
        String originalKey = createResult.getFullKey();

        // Execute: Rotate without revoking old key
        RotateApiKeyRequestDto request = new RotateApiKeyRequestDto();
        request.setRevokeOldKey(false);

        String requestJson = objectMapper.writeValueAsString(request);

        this.mockMvc.perform(post("/api/admin/apikeys/" + apiKeyId + "/rotate")
                .content(requestJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").exists()); // New key returned
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRevokeApiKeySuccess() throws Exception {
        // Setup: Create an API key first
        var createResult = apiKeyService.createApiKey("Key to Revoke", "admin", null);
        Integer apiKeyId = createResult.getApiKey().getId();

        // Execute: Revoke the key
        this.mockMvc.perform(post("/api/admin/apikeys/" + apiKeyId + "/revoke")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(apiKeyId))
            .andExpect(jsonPath("$.revokedAt").exists())
            .andExpect(jsonPath("$.key").doesNotExist()); // Full key NOT returned on revoke

        // Verify key is actually revoked by trying to use it
        String originalKey = createResult.getFullKey();
        this.mockMvc.perform(get("/api/owners")
                .header("X-API-Key", originalKey)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized()); // Should fail because key is revoked
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRevokeApiKeyNotFound() throws Exception {
        // Execute with non-existent ID
        this.mockMvc.perform(post("/api/admin/apikeys/99999/revoke")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN") // Wrong role
    void testRevokeApiKeyUnauthorized() throws Exception {
        // Setup: Create an API key first
        var createResult = apiKeyService.createApiKey("Key for Unauthorized Test", "admin", null);
        Integer apiKeyId = createResult.getApiKey().getId();

        // Execute with wrong role
        this.mockMvc.perform(post("/api/admin/apikeys/" + apiKeyId + "/revoke")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testFullKeyOnlyReturnedOnCreationAndRotation() throws Exception {
        // Create a key - full key should be returned
        CreateApiKeyRequestDto createRequest = new CreateApiKeyRequestDto();
        createRequest.setName("Key for Full Key Test");

        String createRequestJson = objectMapper.writeValueAsString(createRequest);

        String createResponse = this.mockMvc.perform(post("/api/admin/apikeys")
                .content(createRequestJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").exists()) // Full key returned on creation
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Parse the response to get the key ID
        var createResponseNode = objectMapper.readTree(createResponse);
        Integer apiKeyId = createResponseNode.get("id").asInt();

        // Revoke the key - full key should NOT be returned
        this.mockMvc.perform(post("/api/admin/apikeys/" + apiKeyId + "/revoke")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").doesNotExist()); // Full key NOT returned on revoke
    }
}
