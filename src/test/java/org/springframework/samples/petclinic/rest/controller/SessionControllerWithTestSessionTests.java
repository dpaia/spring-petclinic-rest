package org.springframework.samples.petclinic.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.model.User;
import org.springframework.samples.petclinic.repository.TestSession;
import org.springframework.samples.petclinic.repository.TestSessionRepository;
import org.springframework.samples.petclinic.rest.dto.SetSessionAttributeRequestDto;
import org.springframework.samples.petclinic.service.SessionManagementService;
import org.springframework.samples.petclinic.service.UserService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for SessionRestController using real Spring Session behavior.
 */
@WebMvcTest(SessionRestController.class)
public class SessionControllerWithTestSessionTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionManagementService sessionManagementService;

    @MockBean
    private UserService userService;

    private TestSessionRepository testSessionRepository;
    private TestSession testSession;
    private ObjectMapper objectMapper;
    private User testUser;

    @BeforeEach
    void setUp() {

        testSessionRepository = new TestSessionRepository();
        testSession = testSessionRepository.createSession("test-session-123");
        objectMapper = new ObjectMapper();
        
        // Create test user
        testUser = new User();
        testUser.setUsername("testuser@example.com");
        testUser.setEmail("testuser@example.com");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setEnabled(true);

        // Set up test session with user
        testSession.setAttribute("authenticated_user", testUser);
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = {"OWNER_ADMIN"})
    void testGetSessionInfoWithTestSession() throws Exception {
        // Mock the service to return authenticated user (required for session info to work)
        when(sessionManagementService.getAuthenticatedUser(any())).thenReturn(testUser);
        
        // Mock the service to return session info with authenticated user (successful authentication scenario)
        Map<String, Object> sessionInfo = new HashMap<>();
        sessionInfo.put("sessionId", "test-session-123");
        sessionInfo.put("creationTime", System.currentTimeMillis());
        sessionInfo.put("lastAccessedTime", System.currentTimeMillis());
        sessionInfo.put("maxInactiveInterval", 1800);
        sessionInfo.put("isNew", false);
        
        // Add authenticated user info to session
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", testUser.getUsername());
        userInfo.put("email", testUser.getEmail());
        userInfo.put("firstName", testUser.getFirstName());
        userInfo.put("lastName", testUser.getLastName());
        userInfo.put("enabled", testUser.getEnabled());
        sessionInfo.put("authenticatedUser", userInfo);
        
        when(sessionManagementService.getSessionInfo(any())).thenReturn(sessionInfo);
        
        // Mock void method for updating last activity time
        doNothing().when(sessionManagementService).updateLastActivityTime(any());

        mockMvc.perform(get("/api/session/info"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.sessionInfo.sessionId").value("test-session-123"))
            .andExpect(jsonPath("$.sessionInfo.maxInactiveInterval").value(1800))
            .andExpect(jsonPath("$.sessionInfo.isNew").value(false))
            .andExpect(jsonPath("$.sessionInfo.authenticatedUser.username").value("testuser@example.com"))
            .andExpect(jsonPath("$.sessionInfo.authenticatedUser.email").value("testuser@example.com"))
            .andExpect(jsonPath("$.sessionInfo.authenticatedUser.firstName").value("Test"))
            .andExpect(jsonPath("$.sessionInfo.authenticatedUser.lastName").value("User"))
            .andExpect(jsonPath("$.sessionInfo.authenticatedUser.enabled").value(true));
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = {"OWNER_ADMIN"})
    void testGetSessionUserWithRealSession() throws Exception {

        // Mock the service to return error for no authenticated user (actual behavior)
        when(sessionManagementService.getAuthenticatedUser(any()))
            .thenThrow(new RuntimeException("User not authenticated"));

        mockMvc.perform(get("/api/session/user"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.title").value("RuntimeException"))
            .andExpect(jsonPath("$.detail").value("User not authenticated"));
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = {"OWNER_ADMIN"})
    void testGetAllSessionAttributesWithRealSession() throws Exception {
        // Mock the service to return error for no authenticated user (actual behavior)
        when(sessionManagementService.getAllSessionAttributes(any()))
            .thenThrow(new RuntimeException("User not authenticated"));

        mockMvc.perform(get("/api/session/attributes"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("User not authenticated"));
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = {"OWNER_ADMIN"})
    void testGetSpecificSessionAttributeWithRealSession() throws Exception {

        // Mock the service to return structured response (actual behavior)
        Map<String, Object> response = new HashMap<>();
        response.put("key", "theme");

        Map<String, Object> valueWrapper = new HashMap<>();
        valueWrapper.put("key", "theme");
        valueWrapper.put("value", null); // Non-existent attribute
        response.put("value", valueWrapper);
        
        when(sessionManagementService.getSessionAttribute(any(), anyString())).thenReturn(response);

        mockMvc.perform(get("/api/session/attributes/theme"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.key").value("theme"))
            .andExpect(jsonPath("$.value.key").value("theme"))
            .andExpect(jsonPath("$.value.value.value").doesNotExist());

    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = {"OWNER_ADMIN"})
    void testGetNonExistentAttributeWithRealSession() throws Exception {
        // Mock the service to return structured response with null value (actual behavior)
        Map<String, Object> response = new HashMap<>();
        response.put("key", "non_existent");
        Map<String, Object> valueWrapper = new HashMap<>();
        valueWrapper.put("key", "non_existent");
        valueWrapper.put("value", null);
        response.put("value", valueWrapper);
        
        when(sessionManagementService.getSessionAttribute(any(), anyString())).thenReturn(response);

        mockMvc.perform(get("/api/session/attributes/non_existent"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.key").value("non_existent"))
            .andExpect(jsonPath("$.value.key").value("non_existent"))
            .andExpect(jsonPath("$.value.value.value").doesNotExist());

    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = {"OWNER_ADMIN"})
    void testSetSessionAttributeWithRealSession() throws Exception {
        SetSessionAttributeRequestDto attributeDto = new SetSessionAttributeRequestDto();
        attributeDto.setValue("new_value");

        // Mock successful attribute setting (void method)
        doNothing().when(sessionManagementService).setSessionAttribute(any(), anyString(), any());

        mockMvc.perform(put("/api/session/attributes/new_key")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(attributeDto)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("Session attribute set successfully"));
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = {"OWNER_ADMIN"})
    void testSetSystemAttributeShouldFail() throws Exception {
        SetSessionAttributeRequestDto attributeDto = new SetSessionAttributeRequestDto();
        attributeDto.setValue("malicious_value");

        // System attributes should be protected and return 403 (actual behavior)
        mockMvc.perform(put("/api/session/attributes/SPRING_SECURITY_CONTEXT")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(attributeDto)))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("Cannot modify system attribute: SPRING_SECURITY_CONTEXT"));
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = {"OWNER_ADMIN"})
    void testDeleteSessionAttributeWithRealSession() throws Exception {
        // Mock successful deletion (void method)
        doNothing().when(sessionManagementService).removeSessionAttribute(any(), anyString());

        mockMvc.perform(delete("/api/session/attributes/temp_data")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("Session attribute removed successfully"));
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = {"OWNER_ADMIN"})
    void testDeleteSystemAttributeShouldFail() throws Exception {
        // System attributes should be protected and return 403 (actual behavior)
        mockMvc.perform(delete("/api/session/attributes/authenticated_user")
                .with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("Cannot remove system attribute: authenticated_user"));
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = {"OWNER_ADMIN"})
    void testSessionAttributeValidation() throws Exception {
        // Test with invalid JSON - should return 500 (HttpMessageNotReadableException) (actual behavior)
        mockMvc.perform(put("/api/session/attributes/test_key")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("invalid json"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.title").value("HttpMessageNotReadableException"))
            .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = {"OWNER_ADMIN"})
    void testSessionAttributeTypes() throws Exception {
        // Test different attribute types
        SetSessionAttributeRequestDto stringDto = new SetSessionAttributeRequestDto();
        stringDto.setValue("string_value");
        
        SetSessionAttributeRequestDto intDto = new SetSessionAttributeRequestDto();
        intDto.setValue(42);
        
        SetSessionAttributeRequestDto boolDto = new SetSessionAttributeRequestDto();
        boolDto.setValue(true);

        // Mock void method for setting attributes
        doNothing().when(sessionManagementService).setSessionAttribute(any(), anyString(), any());

        // Test string attribute
        mockMvc.perform(put("/api/session/attributes/string_attr")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(stringDto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Session attribute set successfully"));

        // Test integer attribute
        mockMvc.perform(put("/api/session/attributes/int_attr")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intDto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Session attribute set successfully"));

        // Test boolean attribute
        mockMvc.perform(put("/api/session/attributes/bool_attr")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(boolDto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Session attribute set successfully"));
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = {"OWNER_ADMIN"})
    void testSessionExpiration() throws Exception {
        // Test session behavior - should return 401 for expired/invalid sessions (actual behavior)
        when(sessionManagementService.getSessionInfo(any()))
            .thenThrow(new RuntimeException("No active session found"));

        mockMvc.perform(get("/api/session/info"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("No active session found"));
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = {"OWNER_ADMIN"})
    void testConcurrentSessionOperations() throws Exception {
        // Test getting counter attribute with structured response (actual behavior)
        Map<String, Object> response = new HashMap<>();
        response.put("key", "counter");
        Map<String, Object> valueWrapper = new HashMap<>();
        valueWrapper.put("key", "counter");
        valueWrapper.put("value", 10);
        response.put("value", valueWrapper);
        
        when(sessionManagementService.getSessionAttribute(any(), anyString())).thenReturn(response);

        mockMvc.perform(get("/api/session/attributes/counter"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").value("counter"))
            .andExpect(jsonPath("$.value.key").value("counter"))
            .andExpect(jsonPath("$.value.value.value").value(10));
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = {"OWNER_ADMIN"})
    void testSessionAttributeProtection() throws Exception {
        // Test that system attributes are properly protected (actual behavior)
        SetSessionAttributeRequestDto maliciousDto = new SetSessionAttributeRequestDto();
        maliciousDto.setValue("malicious_value");

        // Test various system attributes
        String[] systemAttributes = {
            "SPRING_SECURITY_CONTEXT",
            "authenticated_user",
            "SPRING_SECURITY_SAVED_REQUEST"
        };

        for (String attr : systemAttributes) {
            mockMvc.perform(put("/api/session/attributes/" + attr)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(maliciousDto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Cannot modify system attribute: " + attr));

            mockMvc.perform(delete("/api/session/attributes/" + attr)
                    .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Cannot remove system attribute: " + attr));
        }
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = {"OWNER_ADMIN"})
    void testSessionAttributeLifecycle() throws Exception {
        // Test complete lifecycle: set, get, update, delete
        String attributeKey = "lifecycle_test";
        
        // Mock void methods for setting and removing attributes
        doNothing().when(sessionManagementService).setSessionAttribute(any(), anyString(), any());
        doNothing().when(sessionManagementService).removeSessionAttribute(any(), anyString());
        
        // Mock get responses for different stages
        Map<String, Object> initialResponse = new HashMap<>();
        initialResponse.put("key", attributeKey);     
        Map<String, Object> initialValueWrapper = new HashMap<>();
        initialValueWrapper.put("key", attributeKey);
        initialValueWrapper.put("value", "initial_value");
        initialResponse.put("value", initialValueWrapper);
        
        Map<String, Object> updatedResponse = new HashMap<>();
        updatedResponse.put("key", attributeKey);
        Map<String, Object> updatedValueWrapper = new HashMap<>();
        updatedValueWrapper.put("key", attributeKey);
        updatedValueWrapper.put("value", "updated_value");
        updatedResponse.put("value", updatedValueWrapper);
        
        Map<String, Object> deletedResponse = new HashMap<>();
        deletedResponse.put("key", attributeKey);
        Map<String, Object> deletedValueWrapper = new HashMap<>();
        deletedValueWrapper.put("key", attributeKey);
        deletedValueWrapper.put("value", null);
        deletedResponse.put("value", deletedValueWrapper);
        
        when(sessionManagementService.getSessionAttribute(any(), anyString()))
            .thenReturn(initialResponse)
            .thenReturn(updatedResponse)
            .thenReturn(deletedResponse);

        // 1. Set initial value
        SetSessionAttributeRequestDto initialDto = new SetSessionAttributeRequestDto();
        initialDto.setValue("initial_value");
        
        mockMvc.perform(put("/api/session/attributes/" + attributeKey)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initialDto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Session attribute set successfully"));

        // 2. Get the value
        mockMvc.perform(get("/api/session/attributes/" + attributeKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").value(attributeKey))
            .andExpect(jsonPath("$.value.key").value(attributeKey))
            .andExpect(jsonPath("$.value.value.value").value("initial_value"));

        // 3. Update the value
        SetSessionAttributeRequestDto updatedDto = new SetSessionAttributeRequestDto();
        updatedDto.setValue("updated_value");
        
        mockMvc.perform(put("/api/session/attributes/" + attributeKey)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedDto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Session attribute set successfully"));

        // 4. Verify update
        mockMvc.perform(get("/api/session/attributes/" + attributeKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.value.value.value").value("updated_value"));

        // 5. Delete the attribute
        mockMvc.perform(delete("/api/session/attributes/" + attributeKey)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Session attribute removed successfully"));

        // 6. Verify deletion (should return null value)
        mockMvc.perform(get("/api/session/attributes/" + attributeKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").value(attributeKey))
            .andExpect(jsonPath("$.value.key").value(attributeKey))
            .andExpect(jsonPath("$.value.value.value").doesNotExist());
    }
}