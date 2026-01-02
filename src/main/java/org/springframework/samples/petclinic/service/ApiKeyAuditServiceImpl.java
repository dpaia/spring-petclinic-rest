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
package org.springframework.samples.petclinic.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.samples.petclinic.model.ApiKey;
import org.springframework.samples.petclinic.model.ApiKeyAuditLog;
import org.springframework.samples.petclinic.repository.ApiKeyAuditLogRepository;
import org.springframework.samples.petclinic.repository.ApiKeyRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Service implementation for API key audit logging operations.
 *
 * @author Spring PetClinic Team
 */
@Service
public class ApiKeyAuditServiceImpl implements ApiKeyAuditService {

    private final ApiKeyAuditLogRepository auditLogRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final boolean auditEnabled;
    private final int failureThreshold;
    private final int timeWindowMinutes;

    @Autowired
    public ApiKeyAuditServiceImpl(ApiKeyAuditLogRepository auditLogRepository, 
                                   ApiKeyRepository apiKeyRepository,
                                   @Value("${petclinic.apikey.audit.enabled:true}") boolean auditEnabled,
                                   @Value("${petclinic.apikey.suspicious-activity.failure-threshold:5}") int failureThreshold,
                                   @Value("${petclinic.apikey.suspicious-activity.time-window-minutes:15}") int timeWindowMinutes) {
        this.auditLogRepository = auditLogRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.auditEnabled = auditEnabled;
        this.failureThreshold = failureThreshold;
        this.timeWindowMinutes = timeWindowMinutes;
    }

    @Override
    @Async
    @Transactional
    public void logAuthenticationAttempt(HttpServletRequest request, Integer apiKeyId, 
                                         String keyPrefix, boolean success, String failureReason) {
        if (!auditEnabled) {
            return;
        }

        try {
            // Extract request info
            String requestMethod = request.getMethod();
            String requestPath = request.getRequestURI();
            String requestIp = getClientIpAddress(request);
            String userAgent = request.getHeader("User-Agent");

            // Create audit log entry
            ApiKeyAuditLog auditLog = new ApiKeyAuditLog();
            
            // Link successful attempts to api_key_id; store key_prefix for failed attempts
            if (success && apiKeyId != null) {
                apiKeyRepository.findById(apiKeyId)
                    .ifPresent(auditLog::setApiKey);
            }
            
            auditLog.setKeyPrefix(keyPrefix != null ? keyPrefix : "UNKNOWN");
            auditLog.setRequestMethod(requestMethod);
            auditLog.setRequestPath(requestPath);
            auditLog.setRequestIp(requestIp != null ? requestIp : "UNKNOWN");
            auditLog.setUserAgent(userAgent != null ? userAgent : "UNKNOWN");
            auditLog.setSuccess(success);
            auditLog.setFailureReason(failureReason);
            auditLog.setTimestamp(LocalDateTime.now());

            // Save asynchronously
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Log error but don't fail the request
            // In production, you might want to use a proper logger here
            System.err.println("Failed to log authentication attempt: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean detectSuspiciousActivity(String keyPrefix) {
        if (keyPrefix == null) {
            return false;
        }

        try {
            // Calculate time window
            LocalDateTime timeWindowStart = LocalDateTime.now().minusMinutes(timeWindowMinutes);

            // Count failed attempts within time window
            long failureCount = auditLogRepository.countByKeyPrefixAndSuccessFalseAndTimestampAfter(
                keyPrefix, timeWindowStart);

            return failureCount >= failureThreshold;
        } catch (DataAccessException e) {
            // If we can't check, don't block - return false
            return false;
        }
    }

    /**
     * Extract client IP address from request, handling proxies.
     *
     * @param request the HTTP request
     * @return the client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}

