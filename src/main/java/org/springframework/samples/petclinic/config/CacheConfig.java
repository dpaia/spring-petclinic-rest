package org.springframework.samples.petclinic.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    private final Map<String, CaffeineCacheManager> cacheManagers = new ConcurrentHashMap<>();

    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        Caffeine<Object, Object> caffeineBuilder = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats();
            
        cacheManager.setCaffeine(caffeineBuilder);
        cacheManager.setCacheNames(Arrays.asList("pets", "visits", "vets", "owners", "petTypes", "specialties"));
        
        cacheManagers.put("default", cacheManager);
        
        return cacheManager;
    }
    
    @Bean
    public CacheStatsCollector cacheStatsCollector() {
        return new CacheStatsCollector(cacheManagers);
    }
    
    public static class CacheStatsCollector {
        private final Map<String, CaffeineCacheManager> cacheManagers;
        
        public CacheStatsCollector(Map<String, CaffeineCacheManager> cacheManagers) {
            this.cacheManagers = cacheManagers;
        }
        
        public Map<String, CacheStats> getStats() {
            Map<String, CacheStats> allStats = new ConcurrentHashMap<>();
            
            cacheManagers.forEach((name, manager) -> {
                manager.getCacheNames().forEach(cacheName -> {
                    com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = 
                        (com.github.benmanes.caffeine.cache.Cache<Object, Object>) manager.getCache(cacheName).getNativeCache();
                    allStats.put(cacheName, nativeCache.stats());
                });
            });
            
            return allStats;
        }
        
        public void clearAllCaches() {
            cacheManagers.forEach((name, manager) -> {
                manager.getCacheNames().forEach(cacheName -> {
                    manager.getCache(cacheName).clear();
                });
            });
        }
        
        public void clearCache(String cacheName) {
            cacheManagers.forEach((name, manager) -> {
                if (manager.getCacheNames().contains(cacheName)) {
                    manager.getCache(cacheName).clear();
                }
            });
        }
    }
}