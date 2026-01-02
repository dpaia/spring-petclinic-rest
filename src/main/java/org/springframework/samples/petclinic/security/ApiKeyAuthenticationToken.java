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

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Collections;

/**
 * Authentication token for API key authentication.
 * Holds the raw API key string as the principal.
 *
 * @author Spring PetClinic Team
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final String apiKey;
    private final Object principal;

    /**
     * Creates an unauthenticated token with the API key.
     *
     * @param apiKey the API key from the request header
     */
    public ApiKeyAuthenticationToken(String apiKey) {
        super(Collections.emptyList());
        this.apiKey = apiKey;
        this.principal = apiKey;
        setAuthenticated(false);
    }

    /**
     * Creates an authenticated token with authorities.
     *
     * @param apiKey the API key
     * @param authorities the granted authorities
     * @param principal the principal (typically username or API key identifier)
     */
    public ApiKeyAuthenticationToken(String apiKey, Collection<? extends GrantedAuthority> authorities, Object principal) {
        super(authorities);
        this.apiKey = apiKey;
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null; // API key is the principal, not credentials
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    public String getApiKey() {
        return apiKey;
    }
}

