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
package org.springframework.samples.petclinic.repository;

import java.time.LocalDateTime;
import java.util.Collection;

import org.springframework.dao.DataAccessException;
import org.springframework.samples.petclinic.model.ApiKeyAuditLog;

/**
 * Repository interface for {@link ApiKeyAuditLog} domain objects.
 * Method names are compliant with Spring Data naming conventions.
 *
 * @author Spring PetClinic Team
 */
public interface ApiKeyAuditLogRepository {

    /**
     * Retrieve all audit log entries for a given key prefix after a specific timestamp.
     *
     * @param keyPrefix the key prefix to search for
     * @param timestamp the timestamp to search after
     * @return a {@link Collection} of {@link ApiKeyAuditLog} entries
     * @throws DataAccessException if data access fails
     */
    Collection<ApiKeyAuditLog> findByKeyPrefixAndTimestampAfter(String keyPrefix, LocalDateTime timestamp) throws DataAccessException;

    /**
     * Count failed authentication attempts for a given key prefix after a specific timestamp.
     *
     * @param keyPrefix the key prefix to search for
     * @param timestamp the timestamp to search after
     * @return the count of failed attempts
     * @throws DataAccessException if data access fails
     */
    long countByKeyPrefixAndSuccessFalseAndTimestampAfter(String keyPrefix, LocalDateTime timestamp) throws DataAccessException;

    /**
     * Save an {@link ApiKeyAuditLog} to the data store.
     *
     * @param auditLog the {@link ApiKeyAuditLog} to save
     * @throws DataAccessException if data access fails
     */
    void save(ApiKeyAuditLog auditLog) throws DataAccessException;
}

