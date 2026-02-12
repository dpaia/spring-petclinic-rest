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
package org.springframework.samples.petclinic.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Entity representing an audit log entry for API key authentication attempts.
 * Tracks both successful and failed authentication attempts for security monitoring.
 *
 * @author Spring PetClinic Team
 */
@Entity
@Table(name = "api_key_audit_log")
public class ApiKeyAuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_key_id")
    private ApiKey apiKey;

    @Column(name = "key_prefix")
    @NotEmpty
    @Size(max = 20)
    private String keyPrefix;

    @Column(name = "request_method")
    @NotEmpty
    @Size(max = 10)
    private String requestMethod;

    @Column(name = "request_path")
    @NotEmpty
    @Size(max = 500)
    private String requestPath;

    @Column(name = "request_ip")
    @NotEmpty
    @Size(max = 45)
    private String requestIp;

    @Column(name = "user_agent")
    @Size(max = 500)
    private String userAgent;

    @Column(name = "success")
    @NotNull
    private Boolean success;

    @Column(name = "failure_reason")
    @Size(max = 255)
    private String failureReason;

    @Column(name = "timestamp")
    @NotNull
    private LocalDateTime timestamp;

    public ApiKey getApiKey() {
        return apiKey;
    }

    public void setApiKey(ApiKey apiKey) {
        this.apiKey = apiKey;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getRequestIp() {
        return requestIp;
    }

    public void setRequestIp(String requestIp) {
        this.requestIp = requestIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}

