# Spring Petclinic REST API - Caching Documentation

## Overview

The Spring Petclinic REST API has been enhanced with Caffeine-based caching to improve performance and reduce database load. This document describes the caching strategy, implementation details, and operational considerations.

## Cache Configuration

### Technology Stack
- **Cache Provider**: Caffeine (high-performance, near-optimal caching library)
- **Spring Cache Abstraction**: Integrated with Spring's `@Cacheable` and `@CacheEvict` annotations
- **Statistics**: Built-in cache statistics tracking for monitoring

### Cache Settings
- **Maximum Size**: 1,000 entries per cache
- **Time to Live (TTL)**: 10 minutes after write
- **Eviction Policy**: Size-based eviction using Caffeine's Window TinyLFU policy
- **Statistics**: Enabled for all caches

### Cache Regions

The following cache regions are configured:

1. **pets** - Caches Pet entities
2. **visits** - Caches Visit entities  
3. **vets** - Caches Vet entities
4. **owners** - Caches Owner entities
5. **petTypes** - Caches PetType entities
6. **specialties** - Caches Specialty entities

## Caching Strategy

### Read Operations (Cache Population)
All read operations in `ClinicServiceImpl` are annotated with `@Cacheable`:

- `findAllPets()`, `findPetById(int)` → Cache: "pets"
- `findAllVisits()`, `findVisitById(int)`, `findVisitsByPetId(int)` → Cache: "visits"
- `findAllVets()`, `findVetById(int)`, `findVets()` → Cache: "vets"
- `findAllOwners()`, `findOwnerById(int)`, `findOwnerByLastName(String)` → Cache: "owners"
- `findAllPetTypes()`, `findPetTypeById(int)`, `findPetTypes()` → Cache: "petTypes"
- `findAllSpecialties()`, `findSpecialtyById(int)`, `findSpecialtiesByNameIn(Set)` → Cache: "specialties"

### Write Operations (Cache Eviction)
All write operations use `@CacheEvict(allEntries = true)` to ensure data consistency:

- `savePet()`, `deletePet()` → Evicts all entries from "pets" cache
- `saveVisit()`, `deleteVisit()` → Evicts all entries from "visits" cache
- `saveVet()`, `deleteVet()` → Evicts all entries from "vets" cache
- `saveOwner()`, `deleteOwner()` → Evicts all entries from "owners" cache
- `savePetType()`, `deletePetType()` → Evicts all entries from "petTypes" cache
- `saveSpecialty()`, `deleteSpecialty()` → Evicts all entries from "specialties" cache

## Cache Management Endpoints

### GET /api/cache/stats
Returns cache statistics for all cache regions.

**Response format:**
```json
{
  "pets": {
    "hits": 150,
    "misses": 25,
    "hitRate": 0.857,
    "evictionCount": 0,
    "loadSuccessCount": 25,
    "loadFailureCount": 0,
    "totalLoadTime": 125000000,
    "averageLoadPenalty": 5000000.0
  },
  // ... other cache regions
}
```

### DELETE /api/cache/clear
Clears all caches. Returns HTTP 204 No Content.

### DELETE /api/cache/clear/{cacheName}
Clears a specific cache region. Returns HTTP 204 No Content.

Valid cache names: pets, visits, vets, owners, petTypes, specialties

## Performance Benefits

Based on benchmark tests, the caching implementation provides:

1. **Response Time Improvement**: Cache hits are typically 10-100x faster than database queries
2. **Database Load Reduction**: For typical access patterns, >90% of requests are served from cache
3. **Concurrent Access**: Cache operations are thread-safe and perform well under concurrent load
4. **Memory Efficiency**: Window TinyLFU eviction policy ensures optimal memory usage

## Operational Considerations

### Monitoring
- Monitor cache hit rates via `/api/cache/stats` endpoint
- Low hit rates may indicate:
  - Cache size is too small
  - TTL is too short
  - Access patterns are too random

### Tuning Recommendations
1. **Cache Size**: Adjust based on memory availability and dataset size
2. **TTL**: Balance between data freshness and performance
3. **Eviction Strategy**: Current "evict all" strategy ensures consistency but may impact hit rates

### Best Practices
1. **Clear caches** after bulk data imports or migrations
2. **Monitor memory usage** to ensure caches don't consume excessive heap
3. **Review access patterns** periodically to optimize cache configuration
4. **Use cache stats** to identify performance bottlenecks

### Troubleshooting

**High miss rate:**
- Check if TTL is too aggressive
- Verify cache size is adequate
- Review access patterns

**Stale data issues:**
- Ensure all write operations properly evict cache
- Check if external systems are modifying data directly

**Memory issues:**
- Reduce cache size
- Decrease TTL
- Monitor eviction counts

## Future Enhancements

1. **Selective Eviction**: Implement more granular eviction strategies instead of clearing entire caches
2. **Cache Warming**: Pre-populate caches on application startup
3. **Distributed Caching**: Consider Redis or Hazelcast for multi-instance deployments
4. **Conditional Caching**: Cache based on request parameters or user roles
5. **Cache Metrics**: Integrate with monitoring tools (Prometheus/Grafana)