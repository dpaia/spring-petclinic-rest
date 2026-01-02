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

import java.util.Collection;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.samples.petclinic.model.ApiKey;

/**
 * Repository interface for {@link ApiKey} domain objects.
 * Method names are compliant with Spring Data naming conventions.
 *
 * @author Spring PetClinic Team
 */
public interface ApiKeyRepository {

    /**
     * Retrieve an {@link ApiKey} from the data store by id, only if it is active.
     *
     * @param id the id to search for
     * @return the {@link ApiKey} if found and active
     * @throws DataAccessException if data access fails
     */
    Optional<ApiKey> findByIdAndIsActiveTrue(Integer id) throws DataAccessException;

    /**
     * Retrieve all active {@link ApiKey}s that are not revoked.
     *
     * @return a {@link Collection} of active, non-revoked {@link ApiKey}s
     * @throws DataAccessException if data access fails
     */
    Collection<ApiKey> findByIsActiveTrueAndRevokedAtIsNull() throws DataAccessException;

    /**
     * Save an {@link ApiKey} to the data store, either inserting or updating it.
     *
     * @param apiKey the {@link ApiKey} to save
     * @throws DataAccessException if data access fails
     */
    void save(ApiKey apiKey) throws DataAccessException;

    /**
     * Delete an {@link ApiKey} from the data store.
     *
     * @param apiKey the {@link ApiKey} to delete
     * @throws DataAccessException if data access fails
     */
    void delete(ApiKey apiKey) throws DataAccessException;

    /**
     * Retrieve an {@link ApiKey} from the data store by id.
     *
     * @param id the id to search for
     * @return the {@link ApiKey} if found
     * @throws DataAccessException if data access fails
     */
    Optional<ApiKey> findById(Integer id) throws DataAccessException;
}

