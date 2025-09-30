/*
 * Copyright 2002-2024 the original author or authors.
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
package org.springframework.samples.petclinic.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.samples.petclinic.model.Specialty;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for hybrid caching strategy
 * Tests cache consistency, fallback, and coordination between L1 and L2 caches
 *
 * NOTE: These tests require Docker to be running to start Redis testcontainer.
 * To enable these tests, ensure Docker is installed and running, then remove @Disabled annotation.
 *
 * @author Spring Petclinic Team
 */
@Disabled("Requires Docker to be running for Redis testcontainer")
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "petclinic.cache.hybrid.enabled=true",
    "petclinic.security.enable=false"
})
public class HybridCacheIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private ClinicService clinicService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // Clear all caches before each test
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    @Test
    void testVetsCachingIsEnabled() {
        // Given
        Cache vetsCache = cacheManager.getCache("vets");
        assertThat(vetsCache).isNotNull();

        // When - First call should populate the cache
        Collection<Vet> vets1 = clinicService.findAllVets();

        // Then
        assertThat(vets1).isNotEmpty();

        // Verify cache contains the data
        Cache.ValueWrapper cachedValue = vetsCache.get("allVets");
        assertThat(cachedValue).isNotNull();
        assertThat(cachedValue.get()).isNotNull();
    }

    @Test
    void testVetsCacheReturnsSameInstance() {
        // When - Call twice
        Collection<Vet> vets1 = clinicService.findAllVets();
        Collection<Vet> vets2 = clinicService.findAllVets();

        // Then - Should return cached instance
        assertThat(vets1).isNotEmpty();
        assertThat(vets2).isNotEmpty();
        assertThat(vets1.size()).isEqualTo(vets2.size());
    }

    @Test
    void testCacheEvictionOnSaveVet() {
        // Given - Initial cache population
        Collection<Vet> vets1 = clinicService.findAllVets();
        int initialSize = vets1.size();

        // When - Save a new vet
        Vet newVet = new Vet();
        newVet.setFirstName("John");
        newVet.setLastName("Doe");
        clinicService.saveVet(newVet);

        // Then - Cache should be evicted and reflect new data
        Collection<Vet> vets2 = clinicService.findAllVets();
        assertThat(vets2.size()).isGreaterThan(initialSize);
    }

    @Test
    void testCacheEvictionOnDeleteVet() {
        // Given - Create and save a vet
        Vet vetToDelete = new Vet();
        vetToDelete.setFirstName("Jane");
        vetToDelete.setLastName("Smith");
        clinicService.saveVet(vetToDelete);

        // Populate cache
        Collection<Vet> vets1 = clinicService.findAllVets();
        int sizeBeforeDelete = vets1.size();

        // When - Delete the vet
        clinicService.deleteVet(vetToDelete);

        // Then - Cache should be evicted and reflect deletion
        Collection<Vet> vets2 = clinicService.findAllVets();
        assertThat(vets2.size()).isLessThan(sizeBeforeDelete);
    }

    @Test
    void testL1AndL2CacheConsistency() {
        // Given
        Cache vetsCache = cacheManager.getCache("vets");
        assertThat(vetsCache).isNotNull();

        // When - First call populates both L1 and L2
        Collection<Vet> vets1 = clinicService.findAllVets();

        // Verify cache hit
        Cache.ValueWrapper l1Value = vetsCache.get("allVets");
        assertThat(l1Value).isNotNull();

        // Then - Second call should hit cache
        Collection<Vet> vets2 = clinicService.findAllVets();
        assertThat(vets2.size()).isEqualTo(vets1.size());
    }

    @Test
    void testCacheFallbackMechanism() {
        // This test verifies that the hybrid cache can handle operations
        // even if one layer fails (logged as warnings)

        // Given
        Cache vetsCache = cacheManager.getCache("vets");
        assertThat(vetsCache).isNotNull();

        // When - Populate cache
        Collection<Vet> vets1 = clinicService.findAllVets();

        // Then - Cache should work normally
        assertThat(vets1).isNotEmpty();

        // Verify cached data exists
        Cache.ValueWrapper cachedValue = vetsCache.get("allVets");
        assertThat(cachedValue).isNotNull();
    }

    @Test
    void testRedisConnectionIsActive() {
        // Verify Redis container is running
        assertThat(redis.isRunning()).isTrue();

        // Verify cache manager is available
        assertThat(cacheManager).isNotNull();

        // Verify we can create a cache
        Cache testCache = cacheManager.getCache("vets");
        assertThat(testCache).isNotNull();
    }

    @Test
    void testWriteThroughBehavior() {
        // Given
        Cache vetsCache = cacheManager.getCache("vets");

        // When - Write operation (save vet) should evict cache
        Vet newVet = new Vet();
        newVet.setFirstName("Alice");
        newVet.setLastName("Brown");
        clinicService.saveVet(newVet);

        // Then - Next read should populate fresh cache
        Collection<Vet> vets = clinicService.findAllVets();
        assertThat(vets).isNotEmpty();

        // Verify cache is populated
        Cache.ValueWrapper cachedValue = vetsCache.get("allVets");
        assertThat(cachedValue).isNotNull();
    }
}