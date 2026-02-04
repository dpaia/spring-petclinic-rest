package org.springframework.samples.petclinic.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.model.User;
import org.springframework.samples.petclinic.repository.UserRepository;
import org.springframework.samples.petclinic.security.Roles;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureWebMvc
@TestPropertySource(properties = {
    "petclinic.security.enable=true",
    "petclinic.security.oauth2.enable=true",
    "spring.security.oauth2.client.registration.google.client-id=Add Google Client ID here",
    "spring.security.oauth2.client.registration.google.client-secret=Add Google Client Secret here",
    "spring.security.oauth2.client.registration.google.scope=openid,profile,email",
    "spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
    "petclinic.security.admin-emails=admin@petclinic.com,admin@example.com",
    "petclinic.security.session-timeout=1800"

})
@Transactional
public class OAuth2IntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Roles roles;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .apply(springSecurity())
            .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testLoginEndpointWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
            .andDo(print()) 
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(jsonPath("$.loginUrl").value("/oauth2/authorization/google"));
    }

    @Test
    void testAuthenticationStatusWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/auth/status"))
            .andExpect(status().isOk())
            .andDo(print()) 
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(jsonPath("$.sessionId").doesNotExist());
    }

   /*  @Test
    void testOAuth2LoginFlow() throws Exception {
        // Create OAuth2 user attributes
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "123456789");
        attributes.put("email", "test@example.com");
        attributes.put("given_name", "Test");
        attributes.put("family_name", "User");
        attributes.put("picture", "https://example.com/picture.jpg");

        OAuth2User oauth2User = new DefaultOAuth2User(
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
            attributes,
            "sub"
        );

        // Test authenticated request
        mockMvc.perform(get("/api/auth/status")
                .with(oauth2Login().oauth2User(oauth2User)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.user.email").value("test@example.com"))
            .andExpect(jsonPath("$.user.firstName").value("Test"))
            .andExpect(jsonPath("$.user.lastName").value("User"));
    }
*/
    @Test
    void testOAuth2LoginFlow() throws Exception {

        MockHttpSession session = new MockHttpSession();

        // Create the app User 
        User user = new User();
        user.setUsername("test@example.com");
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);

        // Store in session as your success handler does
        session.setAttribute("authenticated_user", user);

        // Perform authenticated request
        mockMvc.perform(get("/api/auth/status")
            .session(session)
            .with(oauth2Login()))
            .andExpect(status().isOk())
            .andDo(print()) 
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.user.email").value("test@example.com"))
            .andExpect(jsonPath("$.user.firstName").value("Test"))
            .andExpect(jsonPath("$.user.lastName").value("User"));
    }

    @Test
    void testSessionPersistenceAcrossRequests() throws Exception {
    
        MockHttpSession session = new MockHttpSession();

         User user = new User();
        user.setUsername("test@example.com");
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);
        // Store in session as your success handler does
        session.setAttribute("authenticated_user", user);

        // First request - establish session
        mockMvc.perform(get("/api/auth/status")
            .session(session)
            .with(oauth2Login()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true));

        // Second request - verify session persistence
        mockMvc.perform(get("/api/session/user")
            .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value("test@example.com"));
    }

    @Test
    void testLogoutInvalidatesSession() throws Exception {
              
        MockHttpSession session = new MockHttpSession();

         User user = new User();
        user.setUsername("test@example.com");
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);
        // Store in session as your success handler does
        session.setAttribute("authenticated_user", user);

        // Login
        mockMvc.perform(get("/api/auth/status")
            .session(session)
            .with(oauth2Login()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true));

        // Logout
        mockMvc.perform(post("/api/auth/logout")
            .session(session)
             .with(csrf()) 
            .with(oauth2Login()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.authenticated").value(false));

        // Verify session is invalidated
        mockMvc.perform(get("/api/auth/status")
            .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void testRoleAssignment() throws Exception {
              MockHttpSession session = new MockHttpSession();

        User user = new User();
        user.setUsername("test@example.com");
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);
        user.addRole("ROLE_OWNER_ADMIN");

        // Store in session as your success handler does
        session.setAttribute("authenticated_user", user);

        mockMvc.perform(get("/api/auth/status")
            .session(session)
            .with(oauth2Login()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.user.email").value("test@example.com"));

         mockMvc.perform(get("/api/session/user")
            .session(session)
            .with(oauth2Login()))
            .andDo(print()) 
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value("test@example.com"))
            .andExpect(jsonPath("$.user.roles", hasItem("ROLE_OWNER_ADMIN")));

    }

    @Test
    void testUnauthorizedAccessToProtectedEndpoints() throws Exception {
        // Test accessing protected endpoints without authentication
        mockMvc.perform(get("/api/session/info"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/session/user"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/session/attributes"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testSessionExpirationBehavior() throws Exception {
        
        User user = new User();
        user.setUsername("admin@petclinic.com");
        user.setEmail("admin@petclinic.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);
       

        MockHttpSession session = new MockHttpSession();
         // Store in session as your success handler does
        session.setAttribute("authenticated_user", user);
        // Set short session timeout for testing
        session.setMaxInactiveInterval(1); // 1 second for testing

        // Login
        mockMvc.perform(get("/api/auth/status")
                .session(session)
                .with(oauth2Login()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true));

        // Simulate session expiration
        session.invalidate();

        // Verify session has expired
        mockMvc.perform(get("/api/auth/status")
            .session(session)
            .with(oauth2Login()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false));
    }
}