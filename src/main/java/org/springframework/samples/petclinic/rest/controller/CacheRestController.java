package org.springframework.samples.petclinic.rest.controller;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.config.CacheConfig;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cache")
@CrossOrigin(exposedHeaders = "errors, content-type")
public class CacheRestController {

    private final CacheConfig.CacheStatsCollector cacheStatsCollector;

    @Autowired
    public CacheRestController(CacheConfig.CacheStatsCollector cacheStatsCollector) {
        this.cacheStatsCollector = cacheStatsCollector;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, CacheStatsDto>> getCacheStats() {
        Map<String, CacheStats> stats = cacheStatsCollector.getStats();
        Map<String, CacheStatsDto> response = new HashMap<>();
        
        stats.forEach((name, cacheStats) -> {
            CacheStatsDto dto = new CacheStatsDto();
            dto.setHits(cacheStats.hitCount());
            dto.setMisses(cacheStats.missCount());
            dto.setHitRate(cacheStats.hitRate());
            dto.setEvictionCount(cacheStats.evictionCount());
            dto.setLoadSuccessCount(cacheStats.loadSuccessCount());
            dto.setLoadFailureCount(cacheStats.loadFailureCount());
            dto.setTotalLoadTime(cacheStats.totalLoadTime());
            dto.setAverageLoadPenalty(cacheStats.averageLoadPenalty());
            response.put(name, dto);
        });
        
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearAllCaches() {
        cacheStatsCollector.clearAllCaches();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping("/clear/{cacheName}")
    public ResponseEntity<Void> clearCache(@PathVariable String cacheName) {
        cacheStatsCollector.clearCache(cacheName);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
    
    public static class CacheStatsDto {
        private long hits;
        private long misses;
        private double hitRate;
        private long evictionCount;
        private long loadSuccessCount;
        private long loadFailureCount;
        private long totalLoadTime;
        private double averageLoadPenalty;

        public long getHits() {
            return hits;
        }

        public void setHits(long hits) {
            this.hits = hits;
        }

        public long getMisses() {
            return misses;
        }

        public void setMisses(long misses) {
            this.misses = misses;
        }

        public double getHitRate() {
            return hitRate;
        }

        public void setHitRate(double hitRate) {
            this.hitRate = hitRate;
        }

        public long getEvictionCount() {
            return evictionCount;
        }

        public void setEvictionCount(long evictionCount) {
            this.evictionCount = evictionCount;
        }

        public long getLoadSuccessCount() {
            return loadSuccessCount;
        }

        public void setLoadSuccessCount(long loadSuccessCount) {
            this.loadSuccessCount = loadSuccessCount;
        }

        public long getLoadFailureCount() {
            return loadFailureCount;
        }

        public void setLoadFailureCount(long loadFailureCount) {
            this.loadFailureCount = loadFailureCount;
        }

        public long getTotalLoadTime() {
            return totalLoadTime;
        }

        public void setTotalLoadTime(long totalLoadTime) {
            this.totalLoadTime = totalLoadTime;
        }

        public double getAverageLoadPenalty() {
            return averageLoadPenalty;
        }

        public void setAverageLoadPenalty(double averageLoadPenalty) {
            this.averageLoadPenalty = averageLoadPenalty;
        }
    }
}