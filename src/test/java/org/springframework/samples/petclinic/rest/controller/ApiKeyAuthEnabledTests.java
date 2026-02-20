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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Integration tests for API key authentication (enabled).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"spring-data-jpa", "h2"})
@TestPropertySource(properties = {
    "petclinic.apikey.enabled=true",
    "petclinic.apikey.suspicious-activity.failure-threshold=3",
    "petclinic.apikey.suspicious-activity.time-window-minutes=15"
})
@ExtendWith(OutputCaptureExtension.class)
class ApiKeyAuthEnabledTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;
    private ObjectMapper objectMapper;
    private HttpHeaders adminHeaders;
    private int ownerId;
    private int petTypeId;
    private int specialtyId;
    private int vetId;
    private int petId;
    private int visitId;

    @BeforeEach
    void setUp() throws Exception {
        baseUrl = "http://localhost:" + port + "/petclinic";
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        adminHeaders = new HttpHeaders();
        String credentials = Base64.getEncoder().encodeToString("admin:admin".getBytes());
        adminHeaders.set("Authorization", "Basic " + credentials);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));

        ownerId = createOwner("Api", "KeyOwner");
        petTypeId = createPetType("ApiKeyType");
        specialtyId = createSpecialty("ApiKeySpecialty");
        vetId = createVet("Api", "Vet", specialtyId);
        petId = createPet(ownerId, petTypeId, "ApiPet");
        visitId = createVisit(ownerId, petId, "Api Visit");
    }

    @Test
    void testValidApiKeyAccess() throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Valid Key", null);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testApiKeyAccessNonAdminEndpoints() throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Non Admin Access", null);
        String[] endpoints = {
            "/api/owners/" + ownerId,
            "/api/pets/" + petId,
            "/api/visits/" + visitId,
            "/api/pettypes/" + petTypeId,
            "/api/specialties/" + specialtyId,
            "/api/vets/" + vetId
        };

        for (String endpoint : endpoints) {
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + endpoint,
                HttpMethod.GET,
                new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
                String.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void testApiKeyCanMutateOwner() throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Mutating Access", null);

        Map<String, Object> request = new HashMap<>();
        request.put("id", ownerId);
        request.put("firstName", "Api");
        request.put("lastName", "KeyOwner");
        request.put("address", "456 Updated St");
        request.put("city", "Madison");
        request.put("telephone", "1234567890");

        String requestJson = objectMapper.writeValueAsString(request);
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.PUT,
            new HttpEntity<>(requestJson, apiKeyJsonHeaders(keyInfo.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void testApiKeyCannotAccessUsersEndpoint() throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Users Forbidden", null);

        Map<String, Object> request = new HashMap<>();
        request.put("username", "api-key-user");
        request.put("password", "secret");
        request.put("enabled", true);

        String requestJson = objectMapper.writeValueAsString(request);
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/users",
            HttpMethod.POST,
            new HttpEntity<>(requestJson, apiKeyJsonHeaders(keyInfo.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void testInvalidApiKeyUnauthorized() {
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders("invalid-key")),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testNoAuthenticationUnauthorized() {
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testMissingApiKeyBasicAuthWorks() {
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testRevokedKeyUnauthorized() throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Key To Revoke", null);

        ResponseEntity<String> revokeResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + keyInfo.id() + "/revoke",
            HttpMethod.POST,
            new HttpEntity<>(adminHeaders),
            String.class
        );
        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode revokeBody = objectMapper.readTree(revokeResponse.getBody());
        assertThat(revokeBody.hasNonNull("id")).isTrue();
        assertThat(revokeBody.hasNonNull("name")).isTrue();
        assertThat(revokeBody.hasNonNull("createdAt")).isTrue();
        assertThat(revokeBody.hasNonNull("createdBy")).isTrue();
        assertThat(revokeBody.hasNonNull("keyPrefix")).isTrue();
        assertThat(revokeBody.hasNonNull("revokedAt")).isTrue();
        boolean keyAbsent = !revokeBody.has("key");
        boolean keyNull = revokeBody.has("key") && revokeBody.get("key").isNull();
        assertThat(keyAbsent || keyNull).isTrue();

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testExpiredKeyUnauthorized() throws Exception {
        LocalDateTime expiresAt = LocalDateTime.now().minusDays(1).withNano(0);
        ApiKeyInfo keyInfo = createApiKey("Expired Key", expiresAt);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testRotateRevokesOldKey() throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Key To Rotate", null);

        Map<String, Boolean> rotateRequest = new HashMap<>();
        rotateRequest.put("revokeOldKey", true);
        String rotateJson = objectMapper.writeValueAsString(rotateRequest);

        ResponseEntity<String> rotateResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + keyInfo.id() + "/rotate",
            HttpMethod.POST,
            new HttpEntity<>(rotateJson, adminHeaders),
            String.class
        );
        assertThat(rotateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode rotateBody = objectMapper.readTree(rotateResponse.getBody());
        String newKey = rotateBody.get("key").asText();
        assertThat(rotateBody.hasNonNull("id")).isTrue();
        assertThat(rotateBody.hasNonNull("name")).isTrue();
        assertThat(rotateBody.hasNonNull("createdAt")).isTrue();
        assertThat(rotateBody.hasNonNull("createdBy")).isTrue();
        assertThat(rotateBody.hasNonNull("key")).isTrue();
        assertThat(rotateBody.hasNonNull("keyPrefix")).isTrue();
        assertThat(newKey.length()).isGreaterThanOrEqualTo(32);
        assertThat(newKey.startsWith(rotateBody.get("keyPrefix").asText())).isTrue();

        ResponseEntity<String> oldKeyResponse = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
            String.class
        );
        assertThat(oldKeyResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> newKeyResponse = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(newKey)),
            String.class
        );
        assertThat(newKeyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testApiKeyForbiddenOnAdminEndpoints() throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Api Key Non Admin", null);

        Map<String, String> createRequest = new HashMap<>();
        createRequest.put("name", "Should Be Forbidden");
        String createJson = objectMapper.writeValueAsString(createRequest);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            new HttpEntity<>(createJson, apiKeyJsonHeaders(keyInfo.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void testCreateApiKeyResponseFormat() throws Exception {
        JsonNode responseBody = createApiKeyResponse("Format Check", null);

        assertThat(responseBody.hasNonNull("id")).isTrue();
        assertThat(responseBody.hasNonNull("name")).isTrue();
        assertThat(responseBody.hasNonNull("createdAt")).isTrue();
        assertThat(responseBody.hasNonNull("createdBy")).isTrue();
        assertThat(responseBody.hasNonNull("key")).isTrue();
        assertThat(responseBody.hasNonNull("keyPrefix")).isTrue();

        String key = responseBody.get("key").asText();
        String keyPrefix = responseBody.get("keyPrefix").asText();
        assertThat(key.length()).isGreaterThanOrEqualTo(32);
        assertThat(key.startsWith(keyPrefix)).isTrue();
        assertThat(responseBody.get("name").asText()).isEqualTo("Format Check");
    }

    @Test
    void testRotateRevokeNotFound() throws Exception {
        Map<String, Boolean> rotateRequest = new HashMap<>();
        rotateRequest.put("revokeOldKey", true);
        String rotateJson = objectMapper.writeValueAsString(rotateRequest);

        ResponseEntity<String> rotateResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/99999/rotate",
            HttpMethod.POST,
            new HttpEntity<>(rotateJson, adminHeaders),
            String.class
        );
        assertThat(rotateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> revokeResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/99999/revoke",
            HttpMethod.POST,
            new HttpEntity<>(adminHeaders),
            String.class
        );
        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testCreateApiKeyWithoutNameBadRequest() throws Exception {
        String requestJson = objectMapper.writeValueAsString(new HashMap<>());
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            new HttpEntity<>(requestJson, adminHeaders),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testFutureExpiresAtAllowsAccess() throws Exception {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(10).withNano(0);
        ApiKeyInfo keyInfo = createApiKey("Future Expiry", expiresAt);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testSuspiciousActivitySignal() {
        String prefix = "suspici0"; // 8 chars

        for (int i = 0; i < 3; i++) {
            String invalidKey = prefix + String.format("%056d", i);
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/owners/" + ownerId,
                HttpMethod.GET,
                new HttpEntity<>(apiKeyHeaders(invalidKey)),
                String.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        String invalidKey = prefix + String.format("%056d", 99);
        ResponseEntity<String> suspiciousResponse = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(invalidKey)),
            String.class
        );

        assertThat(suspiciousResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(suspiciousResponse.getHeaders().getFirst("X-Suspicious-Activity")).isEqualTo("true");
    }

    @Test
    void testAuditLogFormatAndNoPlaintextKey(CapturedOutput output) throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Audit Log", null);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String logs = output.getOut() + output.getErr();
        String prefix = keyInfo.key().substring(0, 8);

        assertThat(logs).contains("key_prefix=" + prefix)
            .contains("success=true")
            .contains("method=GET")
            .contains("path=/petclinic/api/owners/" + ownerId)
            .contains("client_ip=")
            .contains("user_agent=")
            .contains("failure_reason=")
            .contains("timestamp=")
            .doesNotContain(keyInfo.key());
    }

    @Test
    void testAuditLogForFailedAuthentication(CapturedOutput output) {
        String prefix = "badkey00";
        String invalidKey = prefix + String.format("%056d", 1);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(invalidKey)),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String logs = output.getOut() + output.getErr();
        assertThat(logs).contains("key_prefix=" + prefix)
            .contains("success=false")
            .contains("failure_reason=")
            .doesNotContain(invalidKey);
    }

    private ApiKeyInfo createApiKey(String name, LocalDateTime expiresAt) throws Exception {
        JsonNode responseBody = createApiKeyResponse(name, expiresAt);
        return new ApiKeyInfo(responseBody.get("id").asInt(), responseBody.get("key").asText());
    }

    private JsonNode createApiKeyResponse(String name, LocalDateTime expiresAt) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        if (expiresAt != null) {
            request.put("expiresAt", expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody());
    }
    private int createOwner(String firstName, String lastName) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("firstName", firstName);
        request.put("lastName", lastName);
        request.put("address", "123 Main St");
        request.put("city", "Madison");
        request.put("telephone", "1234567890");

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        return responseBody.get("id").asInt();
    }

    private int createPetType(String name) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/pettypes",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        return responseBody.get("id").asInt();
    }

    private int createSpecialty(String name) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/specialties",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        return responseBody.get("id").asInt();
    }

    private int createVet(String firstName, String lastName, int specialtyId) throws Exception {
        Map<String, Object> specialty = new HashMap<>();
        specialty.put("id", specialtyId);
        specialty.put("name", "ApiKeySpecialty");

        Map<String, Object> request = new HashMap<>();
        request.put("firstName", firstName);
        request.put("lastName", lastName);
        request.put("specialties", java.util.Collections.singletonList(specialty));

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/vets",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        return responseBody.get("id").asInt();
    }

    private int createPet(int ownerId, int petTypeId, String name) throws Exception {
        Map<String, Object> petType = new HashMap<>();
        petType.put("id", petTypeId);
        petType.put("name", "ApiKeyType");

        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("birthDate", "2015-01-01");
        request.put("type", petType);

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId + "/pets",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        return responseBody.get("id").asInt();
    }

    private int createVisit(int ownerId, int petId, String description) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("date", "2015-02-02");
        request.put("description", description);

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId + "/pets/" + petId + "/visits",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        return responseBody.get("id").asInt();
    }

    private HttpHeaders apiKeyHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        return headers;
    }

    private HttpHeaders apiKeyJsonHeaders(String apiKey) {
        HttpHeaders headers = apiKeyHeaders(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private record ApiKeyInfo(int id, String key) {}
}
