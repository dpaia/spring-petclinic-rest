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
package org.springframework.samples.petclinic.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.samples.petclinic.config.CacheConfig;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Pet Clinic cache management service providing programmatic cache operations.
 * Supports manual eviction, batch operations, conditional eviction policies,
 * and comprehensive audit logging.
 */
@Service
public class CacheManagementService {

    private static final Logger logger = LoggerFactory.getLogger(CacheManagementService.class);

    private final CacheManager cacheManager;

    @Autowired
    public CacheManagementService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictCache(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            if (key != null) {
                cache.evict(key);
                logger.info("Cache eviction completed - Cache: '{}', Key: '{}'", cacheName, key);
            } else {
                cache.clear();
                logger.info("Cache clear completed - Cache: '{}'", cacheName);
            }
        } else {
            logger.warn("Attempted to evict non-existent cache: '{}'", cacheName);
        }
    }

    public void evictAllCaches() {
        Collection<String> cacheNames = cacheManager.getCacheNames();
        for (String cacheName : cacheNames) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                logger.info("Cleared cache: '{}'", cacheName);
            }
        }
        logger.info("All caches evicted. Total caches cleared: {}", cacheNames.size());
    }

    public void evictCachesByPattern(String cacheNamePattern) {
        Collection<String> cacheNames = cacheManager.getCacheNames();
        List<String> matchingCaches = cacheNames.stream()
                .filter(name -> name.matches(cacheNamePattern))
                .collect(Collectors.toList());

        for (String cacheName : matchingCaches) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                logger.info("Cleared cache matching pattern '{}': '{}'", cacheNamePattern, cacheName);
            }
        }
        logger.info("Pattern-based cache eviction completed. Pattern: '{}', Caches cleared: {}",
                   cacheNamePattern, matchingCaches.size());
    }

    public void conditionalEvictCache(String cacheName, boolean force, Supplier<Boolean> condition) {
        if (force || (condition != null && condition.get())) {
            evictCache(cacheName, null);
            logger.info("Conditional cache eviction executed - Cache: '{}', Force: {}", cacheName, force);
        } else {
            logger.debug("Conditional cache eviction skipped - Cache: '{}'", cacheName);
        }
    }

    public void conditionalEvictCache(String cacheName, boolean force) {
        conditionalEvictCache(cacheName, force, null);
    }

    public <T> void batchEvictCache(String cacheName, Collection<T> keys) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null && keys != null && !keys.isEmpty()) {
            int evictedCount = 0;
            for (T key : keys) {
                cache.evict(key);
                evictedCount++;
            }
            logger.info("Batch eviction completed for cache '{}' - Individual entries: {}", cacheName, evictedCount);
        } else {
            logger.warn("Batch eviction failed - Cache: '{}' {}, Keys provided: {}",
                       cacheName,
                       cache != null ? "found" : "not found",
                       keys != null ? keys.size() : 0);
        }
    }

    public <T, R> R refreshCacheEntry(String cacheName, T key, Function<T, R> dataLoader) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null && key != null && dataLoader != null) {
            cache.evict(key);
            R refreshedData = dataLoader.apply(key);
            logger.info("Cache refresh completed for cache '{}', key: {} - Found: {}",
                       cacheName, key, refreshedData != null);
            return refreshedData;
        } else {
            logger.warn("Cannot refresh cache - Cache: '{}' {}, Key: {}, DataLoader: {}",
                       cacheName,
                       cache != null ? "found" : "not found",
                       key != null ? "provided" : "null",
                       dataLoader != null ? "provided" : "null");
            return null;
        }
    }

    public void handleDataImportCacheInvalidation(String importType) {
        switch (importType.toLowerCase()) {
            case "vets":
                evictCache(CacheConfig.VETS_CACHE, null);
                logger.info("Post-import cache invalidation completed for vets data");
                break;
            case "owners":
                evictCache(CacheConfig.OWNERS_CACHE, null);
                logger.info("Post-import cache invalidation completed for owners data");
                break;
            case "pets":
                evictCache(CacheConfig.PETS_CACHE, null);
                logger.info("Post-import cache invalidation completed for pets data");
                break;
            case "visits":
                evictCache(CacheConfig.VISITS_CACHE, null);
                logger.info("Post-import cache invalidation completed for visits data");
                break;
            case "specialties":
                evictCache(CacheConfig.SPECIALTIES_CACHE, null);
                logger.info("Post-import cache invalidation completed for specialties data");
                break;
            case "pettypes":
                evictCache(CacheConfig.PET_TYPES_CACHE, null);
                logger.info("Post-import cache invalidation completed for petTypes data");
                break;
            case "all":
                evictAllCaches();
                logger.info("Post-import cache invalidation completed for all data");
                break;
            default:
                logger.warn("Unknown import type for cache invalidation: '{}'", importType);
        }
    }

    public boolean isCachePresent(String cacheName) {
        return cacheManager.getCache(cacheName) != null;
    }

    public Collection<String> getCacheNames() {
        return cacheManager.getCacheNames();
    }

    public Cache getCache(String cacheName) {
        return cacheManager.getCache(cacheName);
    }
}
