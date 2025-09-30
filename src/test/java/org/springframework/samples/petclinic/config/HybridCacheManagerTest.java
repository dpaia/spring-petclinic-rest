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
package org.springframework.samples.petclinic.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HybridCacheManager
 * Tests cache coordination, fallback, and error handling
 *
 * @author Spring Petclinic Team
 */
@ExtendWith(MockitoExtension.class)
public class HybridCacheManagerTest {

    @Mock
    private CacheManager l1CacheManager;

    @Mock
    private CacheManager l2CacheManager;

    private HybridCacheManager hybridCacheManager;

    @BeforeEach
    void setUp() {
        hybridCacheManager = new HybridCacheManager(l1CacheManager, l2CacheManager);
    }

    @Test
    void testGetCacheCreatesHybridCache() {
        // Given
        Cache l1Cache = new ConcurrentMapCache("testCache");
        Cache l2Cache = new ConcurrentMapCache("testCache");

        when(l1CacheManager.getCache("testCache")).thenReturn(l1Cache);
        when(l2CacheManager.getCache("testCache")).thenReturn(l2Cache);

        // When
        Cache cache = hybridCacheManager.getCache("testCache");

        // Then
        assertThat(cache).isNotNull();
        assertThat(cache.getName()).isEqualTo("testCache");
    }

    @Test
    void testGetCacheCachesHybridCacheInstance() {
        // Given
        Cache l1Cache = new ConcurrentMapCache("testCache");
        Cache l2Cache = new ConcurrentMapCache("testCache");

        when(l1CacheManager.getCache(anyString())).thenReturn(l1Cache);
        when(l2CacheManager.getCache(anyString())).thenReturn(l2Cache);

        // When - Call twice
        Cache cache1 = hybridCacheManager.getCache("testCache");
        Cache cache2 = hybridCacheManager.getCache("testCache");

        // Then - Should return same instance
        assertThat(cache1).isSameAs(cache2);

        // Verify L1 and L2 cache managers were only called once
        verify(l1CacheManager, times(1)).getCache("testCache");
        verify(l2CacheManager, times(1)).getCache("testCache");
    }

    @Test
    void testHybridCachePutWritesToBothCaches() {
        // Given
        Cache l1Cache = new ConcurrentMapCache("testCache");
        Cache l2Cache = new ConcurrentMapCache("testCache");

        when(l1CacheManager.getCache("testCache")).thenReturn(l1Cache);
        when(l2CacheManager.getCache("testCache")).thenReturn(l2Cache);

        Cache hybridCache = hybridCacheManager.getCache("testCache");

        // When
        hybridCache.put("key1", "value1");

        // Then
        assertThat(l1Cache.get("key1")).isNotNull();
        assertThat(l1Cache.get("key1").get()).isEqualTo("value1");
        assertThat(l2Cache.get("key1")).isNotNull();
        assertThat(l2Cache.get("key1").get()).isEqualTo("value1");
    }

    @Test
    void testHybridCacheGetFromL1First() {
        // Given
        Cache l1Cache = new ConcurrentMapCache("testCache");
        Cache l2Cache = new ConcurrentMapCache("testCache");

        when(l1CacheManager.getCache("testCache")).thenReturn(l1Cache);
        when(l2CacheManager.getCache("testCache")).thenReturn(l2Cache);

        Cache hybridCache = hybridCacheManager.getCache("testCache");

        // Populate L1 only
        l1Cache.put("key1", "l1Value");

        // When
        Cache.ValueWrapper result = hybridCache.get("key1");

        // Then - Should get from L1
        assertThat(result).isNotNull();
        assertThat(result.get()).isEqualTo("l1Value");
    }

    @Test
    void testHybridCacheGetFallsBackToL2() {
        // Given
        Cache l1Cache = new ConcurrentMapCache("testCache");
        Cache l2Cache = new ConcurrentMapCache("testCache");

        when(l1CacheManager.getCache("testCache")).thenReturn(l1Cache);
        when(l2CacheManager.getCache("testCache")).thenReturn(l2Cache);

        Cache hybridCache = hybridCacheManager.getCache("testCache");

        // Populate L2 only
        l2Cache.put("key1", "l2Value");

        // When
        Cache.ValueWrapper result = hybridCache.get("key1");

        // Then - Should get from L2 and promote to L1
        assertThat(result).isNotNull();
        assertThat(result.get()).isEqualTo("l2Value");

        // Verify promoted to L1
        assertThat(l1Cache.get("key1")).isNotNull();
        assertThat(l1Cache.get("key1").get()).isEqualTo("l2Value");
    }

    @Test
    void testHybridCacheGetReturnNullOnMiss() {
        // Given
        Cache l1Cache = new ConcurrentMapCache("testCache");
        Cache l2Cache = new ConcurrentMapCache("testCache");

        when(l1CacheManager.getCache("testCache")).thenReturn(l1Cache);
        when(l2CacheManager.getCache("testCache")).thenReturn(l2Cache);

        Cache hybridCache = hybridCacheManager.getCache("testCache");

        // When
        Cache.ValueWrapper result = hybridCache.get("nonExistentKey");

        // Then
        assertThat(result).isNull();
    }

    @Test
    void testHybridCacheEvictRemovesFromBothCaches() {
        // Given
        Cache l1Cache = new ConcurrentMapCache("testCache");
        Cache l2Cache = new ConcurrentMapCache("testCache");

        when(l1CacheManager.getCache("testCache")).thenReturn(l1Cache);
        when(l2CacheManager.getCache("testCache")).thenReturn(l2Cache);

        Cache hybridCache = hybridCacheManager.getCache("testCache");

        // Populate both caches
        l1Cache.put("key1", "value1");
        l2Cache.put("key1", "value1");

        // When
        hybridCache.evict("key1");

        // Then
        assertThat(l1Cache.get("key1")).isNull();
        assertThat(l2Cache.get("key1")).isNull();
    }

    @Test
    void testHybridCacheClearClearseBothCaches() {
        // Given
        Cache l1Cache = new ConcurrentMapCache("testCache");
        Cache l2Cache = new ConcurrentMapCache("testCache");

        when(l1CacheManager.getCache("testCache")).thenReturn(l1Cache);
        when(l2CacheManager.getCache("testCache")).thenReturn(l2Cache);

        Cache hybridCache = hybridCacheManager.getCache("testCache");

        // Populate both caches
        l1Cache.put("key1", "value1");
        l1Cache.put("key2", "value2");
        l2Cache.put("key1", "value1");
        l2Cache.put("key2", "value2");

        // When
        hybridCache.clear();

        // Then
        assertThat(l1Cache.get("key1")).isNull();
        assertThat(l1Cache.get("key2")).isNull();
        assertThat(l2Cache.get("key1")).isNull();
        assertThat(l2Cache.get("key2")).isNull();
    }

    @Test
    void testHybridCacheHandlesNullL1Cache() {
        // Given
        Cache l2Cache = new ConcurrentMapCache("testCache");

        when(l1CacheManager.getCache("testCache")).thenReturn(null);
        when(l2CacheManager.getCache("testCache")).thenReturn(l2Cache);

        Cache hybridCache = hybridCacheManager.getCache("testCache");

        // When - Put with null L1
        hybridCache.put("key1", "value1");

        // Then - Should work with L2 only
        assertThat(l2Cache.get("key1")).isNotNull();
        assertThat(l2Cache.get("key1").get()).isEqualTo("value1");
    }

    @Test
    void testHybridCacheHandlesNullL2Cache() {
        // Given
        Cache l1Cache = new ConcurrentMapCache("testCache");

        when(l1CacheManager.getCache("testCache")).thenReturn(l1Cache);
        when(l2CacheManager.getCache("testCache")).thenReturn(null);

        Cache hybridCache = hybridCacheManager.getCache("testCache");

        // When - Put with null L2
        hybridCache.put("key1", "value1");

        // Then - Should work with L1 only
        assertThat(l1Cache.get("key1")).isNotNull();
        assertThat(l1Cache.get("key1").get()).isEqualTo("value1");
    }
}