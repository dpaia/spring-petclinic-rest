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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Hybrid cache manager that combines L1 (local) and L2 (distributed) caching
 * Provides automatic fallback between cache layers
 *
 * @author Spring Petclinic Team
 */
public class HybridCacheManager implements CacheManager {

    private static final Logger logger = LoggerFactory.getLogger(HybridCacheManager.class);

    private final CacheManager l1CacheManager;
    private final CacheManager l2CacheManager;
    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();

    public HybridCacheManager(CacheManager l1CacheManager, CacheManager l2CacheManager) {
        this.l1CacheManager = l1CacheManager;
        this.l2CacheManager = l2CacheManager;
    }

    @Override
    public Cache getCache(String name) {
        return cacheMap.computeIfAbsent(name, cacheName -> {
            Cache l1Cache = l1CacheManager.getCache(cacheName);
            Cache l2Cache = l2CacheManager.getCache(cacheName);
            return new HybridCache(cacheName, l1Cache, l2Cache);
        });
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(cacheMap.keySet());
    }

    /**
     * Hybrid cache implementation that coordinates between L1 and L2 caches
     */
    static class HybridCache implements Cache {

        private static final Logger logger = LoggerFactory.getLogger(HybridCache.class);

        private final String name;
        private final Cache l1Cache;
        private final Cache l2Cache;

        HybridCache(String name, Cache l1Cache, Cache l2Cache) {
            this.name = name;
            this.l1Cache = l1Cache;
            this.l2Cache = l2Cache;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return l1Cache.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            // Try L1 first
            if (l1Cache != null) {
                ValueWrapper value = l1Cache.get(key);
                if (value != null) {
                    logger.debug("Cache hit in L1 for key: {}", key);
                    return value;
                }
            }

            // Fallback to L2
            if (l2Cache != null) {
                try {
                    ValueWrapper value = l2Cache.get(key);
                    if (value != null) {
                        logger.debug("Cache hit in L2 for key: {}, promoting to L1", key);
                        // Promote to L1
                        if (l1Cache != null) {
                            l1Cache.put(key, value.get());
                        }
                        return value;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to retrieve from L2 cache for key: {}, error: {}", key, e.getMessage());
                }
            }

            logger.debug("Cache miss for key: {}", key);
            return null;
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            ValueWrapper wrapper = get(key);
            return wrapper != null ? (T) wrapper.get() : null;
        }

        @Override
        public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
            ValueWrapper existingValue = get(key);
            if (existingValue != null) {
                return (T) existingValue.get();
            }

            try {
                T value = valueLoader.call();
                put(key, value);
                return value;
            } catch (Exception e) {
                throw new Cache.ValueRetrievalException(key, valueLoader, e);
            }
        }

        @Override
        public void put(Object key, Object value) {
            // Write to both caches
            if (l1Cache != null) {
                l1Cache.put(key, value);
                logger.debug("Put in L1 cache for key: {}", key);
            }

            if (l2Cache != null) {
                try {
                    l2Cache.put(key, value);
                    logger.debug("Put in L2 cache for key: {}", key);
                } catch (Exception e) {
                    logger.warn("Failed to put in L2 cache for key: {}, error: {}", key, e.getMessage());
                }
            }
        }

        @Override
        public void evict(Object key) {
            // Evict from both caches
            if (l1Cache != null) {
                l1Cache.evict(key);
                logger.debug("Evicted from L1 cache for key: {}", key);
            }

            if (l2Cache != null) {
                try {
                    l2Cache.evict(key);
                    logger.debug("Evicted from L2 cache for key: {}", key);
                } catch (Exception e) {
                    logger.warn("Failed to evict from L2 cache for key: {}, error: {}", key, e.getMessage());
                }
            }
        }

        @Override
        public void clear() {
            if (l1Cache != null) {
                l1Cache.clear();
                logger.debug("Cleared L1 cache");
            }

            if (l2Cache != null) {
                try {
                    l2Cache.clear();
                    logger.debug("Cleared L2 cache");
                } catch (Exception e) {
                    logger.warn("Failed to clear L2 cache, error: {}", e.getMessage());
                }
            }
        }
    }
}