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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled service for automatic cache invalidation.
 * Can be enabled/disabled via application properties
 */
@Service
@EnableScheduling
@ConditionalOnProperty(value = "petclinic.cache.scheduled.enabled", havingValue = "true", matchIfMissing = false)
public class CacheScheduledService {

    private static final Logger logger = LoggerFactory.getLogger(CacheScheduledService.class);

    private final CacheManagementService cacheManagementService;

    @Autowired
    public CacheScheduledService(CacheManagementService cacheManagementService) {
        this.cacheManagementService = cacheManagementService;
    }

    @Scheduled(fixedRateString = "${petclinic.cache.scheduled.rate:3600000}")
    public void scheduledCacheEviction() {
        logger.info("Executing scheduled cache eviction");
        try {
            cacheManagementService.conditionalEvictCache("vets", false);
            logger.info("Scheduled cache eviction completed successfully");
        } catch (Exception e) {
            logger.error("Error during scheduled cache eviction", e);
        }
    }

    @Scheduled(cron = "${petclinic.cache.scheduled.cron:0 0 2 * * ?}")
    public void dailyCacheRefresh() {
        logger.info("Executing daily cache refresh");
        try {
            cacheManagementService.evictAllCaches();
            logger.info("Daily cache refresh completed successfully");
        } catch (Exception e) {
            logger.error("Error during daily cache refresh", e);
        }
    }
}
