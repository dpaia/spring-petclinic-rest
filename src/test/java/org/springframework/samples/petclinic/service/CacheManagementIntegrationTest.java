/*
 * Copyright 2002-2017 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.samples.petclinic.cache.CacheManagementService;
import org.springframework.samples.petclinic.config.CacheConfig;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.repository.VetRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for cache management functionality in ClinicServiceImpl.
 */
@SpringBootTest
@ActiveProfiles({"hsqldb", "spring-data-jpa"})
@TestPropertySource(properties = {
    "petclinic.security.enable=false",
    "petclinic.cache.scheduled.enabled=false"
})
@Transactional
class CacheManagementIntegrationTest {

    @Autowired
    private ClinicServiceImpl clinicService;

    @Autowired
    private CacheManagementService cacheManagementService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private VetRepository vetRepository;

    @BeforeEach
    void setUp() {
        // Clear all caches before each test using the cache management service
        cacheManagementService.evictAllCaches();
    }

    @Test
    void testVetCachingBehavior() {
        // Given
        Collection<Vet> vets = clinicService.findAllVets();
        assertNotNull(vets);

        // Verify cache was populated
        Cache vetsCache = cacheManager.getCache(CacheConfig.VETS_CACHE);
        assertNotNull(vetsCache);
        assertNotNull(vetsCache.get("allVets"));

        // Clear repository call tracking and call again
        Collection<Vet> cachedVets = clinicService.findAllVets();
        assertEquals(vets.size(), cachedVets.size());
    }

    @Test
    void testProgrammaticCacheEviction() {
        // Given - populate cache
        clinicService.findAllVets();
        Cache vetsCache = cacheManager.getCache(CacheConfig.VETS_CACHE);
        assertNotNull(vetsCache.get("allVets"));

        // When - evict cache programmatically
        cacheManagementService.evictCache(CacheConfig.VETS_CACHE, "allVets");

        // Then - cache should be empty
        assertNull(vetsCache.get("allVets"));
    }

    @Test
    void testCacheClearOperation() {
        // Given - populate cache
        clinicService.findAllVets();
        Cache vetsCache = cacheManager.getCache(CacheConfig.VETS_CACHE);
        assertNotNull(vetsCache.get("allVets"));

        // When - clear entire cache
        cacheManagementService.evictCache(CacheConfig.VETS_CACHE, null);

        // Then - cache should be empty
        assertNull(vetsCache.get("allVets"));
    }

    @Test
    void testBatchEvictVetCache() {
        // Given - populate individual vet caches
        Collection<Vet> allVets = vetRepository.findAll();
        if (!allVets.isEmpty()) {
            Vet firstVet = allVets.iterator().next();
            clinicService.findVetById(firstVet.getId());

            Cache vetsCache = cacheManager.getCache(CacheConfig.VETS_CACHE);
            assertNotNull(vetsCache.get(firstVet.getId()));

            // When - batch evict
            clinicService.batchEvictVetCache(Arrays.asList(firstVet.getId()));

            // Then - individual cache entries should be evicted
            assertNull(vetsCache.get(firstVet.getId()));
        }
    }

    @Test
    void testEvictAllCaches() {
        // Given - populate multiple caches
        clinicService.findAllVets();

        Cache vetsCache = cacheManager.getCache(CacheConfig.VETS_CACHE);
        assertNotNull(vetsCache.get("allVets"));

        // When - evict all caches
        cacheManagementService.evictAllCaches();

        // Then - all caches should be empty
        assertNull(vetsCache.get("allVets"));
    }

    @Test
    void testConditionalCacheEviction() {
        // Given - populate cache
        clinicService.findAllVets();
        Cache vetsCache = cacheManager.getCache(CacheConfig.VETS_CACHE);
        assertNotNull(vetsCache.get("allVets"));

        // When - conditional evict with force=true
        clinicService.conditionalEvictVetsCache(true);

        // Then - cache should be evicted
        assertNull(vetsCache.get("allVets"));
    }

    @Test
    void testCacheRefresh() {
        // Given - populate cache and get a vet ID
        Collection<Vet> allVets = vetRepository.findAll();
        if (!allVets.isEmpty()) {
            Vet firstVet = allVets.iterator().next();
            clinicService.findVetById(firstVet.getId());

            Cache vetsCache = cacheManager.getCache(CacheConfig.VETS_CACHE);
            assertNotNull(vetsCache.get(firstVet.getId()));

            // When - refresh cache (this evicts and re-fetches)
            clinicService.refreshVetCache(firstVet.getId());

            // Then - verify the refresh operation completed successfully
            // The refreshVetCache method calls findVetById internally which should repopulate cache
            Vet refreshedVet = clinicService.findVetById(firstVet.getId());
            assertNotNull(refreshedVet);
            assertEquals(firstVet.getId(), refreshedVet.getId());
        }
    }

    @Test
    void testDataImportCacheInvalidation() {
        // Given - populate cache
        clinicService.findAllVets();
        Cache vetsCache = cacheManager.getCache(CacheConfig.VETS_CACHE);
        assertNotNull(vetsCache.get("allVets"));

        // When - handle vets import
        cacheManagementService.handleDataImportCacheInvalidation("vets");

        // Then - vets cache should be invalidated
        assertNull(vetsCache.get("allVets"));
    }

    @Test
    void testPatternBasedCacheEviction() {
        // Given - populate cache
        clinicService.findAllVets();
        Cache vetsCache = cacheManager.getCache(CacheConfig.VETS_CACHE);
        assertNotNull(vetsCache.get("allVets"));

        // When - evict caches matching pattern
        cacheManagementService.evictCachesByPattern(CacheConfig.VETS_CACHE);

        // Then - matching caches should be evicted
        assertNull(vetsCache.get("allVets"));
    }

    @Test
    void testCacheEvictionWithNonExistentCache() {
        // When/Then - should not throw exception
        assertDoesNotThrow(() -> {
            cacheManagementService.evictCache("nonexistent", "key");
        });
    }

    @Test
    void testDirectCacheManagementServiceUsage() {
        // Given - populate cache via service
        clinicService.findAllVets();
        Cache vetsCache = cacheManager.getCache(CacheConfig.VETS_CACHE);
        assertNotNull(vetsCache.get("allVets"));

        // When - evict using CacheManagementService directly
        cacheManagementService.evictCache(CacheConfig.VETS_CACHE, "allVets");

        // Then - cache should be empty
        assertNull(vetsCache.get("allVets"));
    }

    @Test
    void testGenericBatchEviction() {
        // Given - populate cache and get some IDs
        Collection<Vet> allVets = vetRepository.findAll();
        if (!allVets.isEmpty()) {
            Vet firstVet = allVets.iterator().next();
            clinicService.findVetById(firstVet.getId());

            Cache vetsCache = cacheManager.getCache(CacheConfig.VETS_CACHE);
            assertNotNull(vetsCache.get(firstVet.getId()));

            // When - use generic batch eviction
            cacheManagementService.batchEvictCache(CacheConfig.VETS_CACHE, Arrays.asList(firstVet.getId()));

            // Then - cache entry should be evicted
            assertNull(vetsCache.get(firstVet.getId()));
        }
    }

    @Test
    void testGenericCacheRefresh() {
        // Given - populate cache
        Collection<Vet> allVets = vetRepository.findAll();
        if (!allVets.isEmpty()) {
            Vet firstVet = allVets.iterator().next();
            clinicService.findVetById(firstVet.getId());

            Cache vetsCache = cacheManager.getCache(CacheConfig.VETS_CACHE);
            assertNotNull(vetsCache.get(firstVet.getId()));

            // When - refresh using generic method
            Vet refreshedVet = cacheManagementService.refreshCacheEntry(CacheConfig.VETS_CACHE, firstVet.getId(),
                (id) -> clinicService.findVetById(id));

            // Then - refresh should complete successfully
            assertNotNull(refreshedVet);
            assertEquals(firstVet.getId(), refreshedVet.getId());
        }
    }
}
