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

import java.util.Arrays;
import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
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

    @Value("${petclinic.apikey.enabled:true}")
    private boolean enabled;

    @Autowired
    public ApiKeyAuthenticationProvider(ApiKeyService apiKeyService, ApiKeyAuditService auditService) {
        this.apiKeyService = apiKeyService;
        this.auditService = auditService;
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
            // Check for suspicious activity before validation
            if (request != null && auditService.detectSuspiciousActivity(keyPrefix)) {
                // Log suspicious activity attempt
                auditService.logAuthenticationAttempt(request, null, keyPrefix, false, "SUSPICIOUS_ACTIVITY");
                throw new BadCredentialsException("Invalid API key");
            }

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

            // API keys get limited machine-to-machine access (following principle of least privilege)
            // They should only have basic API client access, not admin privileges
            Collection<GrantedAuthority> authorities = Arrays.asList(
                new SimpleGrantedAuthority("ROLE_API_CLIENT")
            );

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


    @Override
    public boolean supports(Class<?> authentication) {
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
