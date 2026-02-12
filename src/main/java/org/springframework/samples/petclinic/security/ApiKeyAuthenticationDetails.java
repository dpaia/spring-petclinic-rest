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

import org.springframework.security.web.authentication.WebAuthenticationDetails;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Custom authentication details that stores the HttpServletRequest
 * for use in authentication provider (e.g., for audit logging).
 *
 * @author Spring PetClinic Team
 */
public class ApiKeyAuthenticationDetails extends WebAuthenticationDetails {

    private final HttpServletRequest request;

    /**
     * Creates authentication details from the HTTP request.
     *
     * @param request the HTTP request
     */
    public ApiKeyAuthenticationDetails(HttpServletRequest request) {
        super(request);
        this.request = request;
    }

    /**
     * Gets the HTTP request.
     *
     * @return the HTTP request
     */
    public HttpServletRequest getRequest() {
        return request;
    }
}

