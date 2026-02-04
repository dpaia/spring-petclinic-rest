package org.springframework.samples.petclinic.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.samples.petclinic.model.User;
import org.springframework.samples.petclinic.service.UserService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private UserService userService;

    @Value("${petclinic.security.admin-emails:admin@petclinic.com,admin@example.com}")
    private List<String> adminEmails;

    @Value("${petclinic.security.session-timeout:1800}") // in seconds
    private int sessionTimeout; // Default 30 minutes

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        HttpSession session = request.getSession();
        User user = null;
        
        final ObjectMapper objectMapper = new ObjectMapper();
        
        // Handle OidcUser (for OpenID Connect providers like Google)
        if (authentication.getPrincipal() instanceof OidcUser) {
            OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
            user = findOrCreateUserFromOidcUser(oidcUser);
        }
        // Handle OAuth2 user (MockMvc tests or non-OIDC providers)
        else if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            user = findOrCreateUserFromOAuth2User(oauth2User);
        }
        
        Map<String, Object> responseData = new HashMap<>();

        if (user != null) {

            // Load roles from DB and convert to Spring Authorities
            var authorities = user.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority(role.getName())) // DB already has ROLE_ prefix
            .toList();

        // Replace authentication with DB-backed auth
        UsernamePasswordAuthenticationToken newAuth =
            new UsernamePasswordAuthenticationToken(user, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(newAuth);
            // Store authenticated user in session
            session.setAttribute("authenticated_user", user);

            // Store session metadata
            session.setAttribute("session_created_time", LocalDateTime.now());
            session.setAttribute("last_activity_time", LocalDateTime.now());
            session.setMaxInactiveInterval(sessionTimeout);
            
            // Initialize default user preferences
            Map<String, Object> preferences = new HashMap<>();
            preferences.put("theme", "light");
            preferences.put("language", "en");
            preferences.put("timezone", "UTC");
            preferences.put("notifications", true);
            session.setAttribute("user_preferences", preferences);

            // Prepare response
            responseData.put("success", true);
            responseData.put("message", "Authentication successful");
            
            Map<String, Object> userResponse = new HashMap<>();
            userResponse.put("username", user.getUsername());
            userResponse.put("email", user.getEmail());
            userResponse.put("firstName", user.getFirstName());
            userResponse.put("lastName", user.getLastName());
            userResponse.put("pictureUrl", user.getPictureUrl());
            userResponse.put("oauthProvider", user.getOauthProvider());
            userResponse.put("roles", user.getRoles().stream()
                .map(role -> role.getName())
                .toList());
            
            responseData.put("user", userResponse);
        }
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(responseData));

    }
    
    private User findOrCreateUserFromOidcUser(OidcUser oidcUser) {
        String email = oidcUser.getEmail();
        String name = oidcUser.getFullName();
        String oauthId = oidcUser.getSubject();
        String pictureUrl = oidcUser.getPicture();
        String provider = "google"; // Assuming Google for OIDC
        
        return findOrCreateUser(email, name, oauthId, provider, pictureUrl);
    }
    
    private User findOrCreateUser(String email, String name, String oauthId, String provider, String pictureUrl) {
        if (email == null) {
            return null;
        }
        
        // Try to find existing user by OAuth ID and provider
        User user = userService.findByOauthIdAndOauthProvider(oauthId, provider);
        
        if (user == null) {
            // Try to find by email (existing user linking OAuth account)
            user = userService.findByEmail(email);
            
            if (user != null) {
                // Link existing user with OAuth account
                user.setOauthId(oauthId);
                user.setOauthProvider(provider);
            } else {
                // Create new user
                user = createNewUser(email, name, oauthId, provider, pictureUrl);
            }
        }
        
        // Update user information from OAuth provider
        updateUserFromOAuth(user, email, name, pictureUrl);
        
        return userService.saveUser(user);
    }
    
    private User createNewUser(String email, String name, String oauthId, String provider, String pictureUrl) {
        User user = new User();
        user.setUsername(email); // Use email as username
        user.setEmail(email);
        user.setOauthId(oauthId);
        user.setOauthProvider(provider);
        user.setPictureUrl(pictureUrl);
        user.setEnabled(true);
        
        // Parse name into first and last name
        if (name != null) {
            String[] nameParts = name.split(" ", 2);
            user.setFirstName(nameParts[0]);
            if (nameParts.length > 1) {
                user.setLastName(nameParts[1]);
            }
        }
        
        // Assign roles based on email
        if (adminEmails.contains(email.toLowerCase())) {
            user.addRole("ROLE_ADMIN");
            user.addRole("ROLE_VET_ADMIN");
            user.addRole("ROLE_OWNER_ADMIN");
        } else {
            user.addRole("ROLE_OWNER_ADMIN");
        }
        
        return user;
    }
    
    private void updateUserFromOAuth(User user, String email, String name, String pictureUrl) {
        user.setEmail(email);
        user.setPictureUrl(pictureUrl);
        
        if (name != null) {
            String[] nameParts = name.split(" ", 2);
            user.setFirstName(nameParts[0]);
            if (nameParts.length > 1) {
                user.setLastName(nameParts[1]);
            }
        }
    }

    private User findOrCreateUserFromOAuth2User(OAuth2User oauth2User) {

    String email = oauth2User.getAttribute("email");
    String name = oauth2User.getAttribute("name");
    String provider = oauth2User.getAttribute("provider");
    String pictureUrl = oauth2User.getAttribute("picture");
    String oauthId = oauth2User.getAttribute("sub");
    
    return findOrCreateUser(email, name, oauthId, provider, pictureUrl);
}
}