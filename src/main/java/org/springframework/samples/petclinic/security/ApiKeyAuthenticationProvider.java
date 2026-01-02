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
package org.springframework.samples.petclinic.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.samples.petclinic.model.ApiKey;
import org.springframework.samples.petclinic.service.ApiKeyAuditService;
import org.springframework.samples.petclinic.service.ApiKeyService;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Authentication provider for API key authentication.
 * Validates API keys against the database and creates authenticated security context.
 *
 * @author Spring PetClinic Team
 */
@Component
@Profile("spring-data-jpa")
@ConditionalOnProperty(name = "petclinic.apikey.enabled", havingValue = "true", matchIfMissing = true)
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private final ApiKeyService apiKeyService;
    private final ApiKeyAuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${petclinic.apikey.enabled:true}")
    private boolean enabled;

    @Autowired
    public ApiKeyAuthenticationProvider(ApiKeyService apiKeyService, ApiKeyAuditService auditService,
                                        JdbcTemplate jdbcTemplate) {
        this.apiKeyService = apiKeyService;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!enabled) {
            return null;
        }

        if (!(authentication instanceof ApiKeyAuthenticationToken)) {
            return null;
        }

        ApiKeyAuthenticationToken token = (ApiKeyAuthenticationToken) authentication;
        String apiKey = token.getApiKey();

        // Extract HttpServletRequest from authentication details
        HttpServletRequest request = null;
        if (authentication.getDetails() instanceof ApiKeyAuthenticationDetails) {
            request = ((ApiKeyAuthenticationDetails) authentication.getDetails()).getRequest();
        }

        if (apiKey == null || apiKey.isEmpty()) {
            if (request != null) {
                auditService.logAuthenticationAttempt(request, null, null, false, "MISSING_KEY");
            }
            throw new BadCredentialsException("API key is required");
        }

        // Extract prefix for audit logging
        String keyPrefix = apiKey.length() >= 8 ? apiKey.substring(0, 8) : "UNKNOWN";

        try {
            // Validate API key
            var apiKeyOpt = apiKeyService.validateApiKey(apiKey);

            if (apiKeyOpt.isEmpty()) {
                // Determine failure reason
                String failureReason = "INVALID_KEY";
                if (request != null) {
                    auditService.logAuthenticationAttempt(request, null, keyPrefix, false, failureReason);
                }
                throw new BadCredentialsException("Invalid API key");
            }

            ApiKey apiKeyEntity = apiKeyOpt.get();

            // Update last used timestamp
            apiKeyService.updateLastUsedAt(apiKeyEntity.getId());

            // Load roles for the user who created the API key
            Collection<GrantedAuthority> authorities = loadUserAuthorities(apiKeyEntity.getCreatedBy());

            // Log successful authentication
            if (request != null) {
                auditService.logAuthenticationAttempt(request, apiKeyEntity.getId(), keyPrefix, true, null);
            }

            // Create authenticated token
            return new ApiKeyAuthenticationToken(apiKey, authorities, apiKeyEntity.getCreatedBy());

        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            if (request != null) {
                auditService.logAuthenticationAttempt(request, null, keyPrefix, false, "VALIDATION_ERROR");
            }
            throw new BadCredentialsException("API key validation failed", e);
        }
    }

    /**
     * Load user authorities (roles) from the database.
     * Uses the same query as BasicAuthenticationConfig for consistency.
     *
     * @param username the username to load roles for
     * @return collection of granted authorities
     */
    private Collection<GrantedAuthority> loadUserAuthorities(String username) {
        try {
            List<String> roles = jdbcTemplate.queryForList(
                "SELECT role FROM roles WHERE username = ?",
                String.class,
                username
            );

            if (roles.isEmpty()) {
                // If no roles found, return empty collection (user has no permissions)
                return Collections.emptyList();
            }

            List<GrantedAuthority> authorities = new ArrayList<>();
            for (String role : roles) {
                authorities.add(new SimpleGrantedAuthority(role));
            }
            return authorities;
        } catch (Exception e) {
            // If we can't load roles, return empty collection (fail secure)
            return Collections.emptyList();
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}

