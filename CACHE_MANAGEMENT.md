# Cache Management Documentation

## Overview

The Spring Petclinic REST API includes advanced cache management capabilities using a dedicated `CacheManagementService` that wraps Spring's CacheManager. This implementation provides manual and programmatic cache eviction strategies, batch operations, conditional eviction policies, and optional scheduled invalidation with full generic support.

## Architecture

### Core Components

- **`CacheManagementService`**: Generic cache management service providing all programmatic cache operations
- **`ClinicServiceImpl`**: Business service that delegates cache operations to CacheManagementService
- **`CacheScheduledService`**: Optional scheduled cache invalidation service
- **`CacheConfig`**: Configuration class enabling caching and defining cache names

## Features

### 1. Generic Programmatic Cache Operations

The `CacheManagementService` provides the following generic cache management methods:

#### Basic Cache Operations

- **`evictCache(String cacheName, Object key)`**: Evicts a specific cache entry or clears entire cache if key is null
- **`evictAllCaches()`**: Clears all cache entries across all configured caches
- **`refreshCacheEntry(String cacheName, T key, Function<T, R> dataLoader)`**: Generic cache refresh with custom data loader
- **`isCachePresent(String cacheName)`**: Checks if a cache exists
- **`getCacheNames()`**: Returns all available cache names

#### Batch Operations

- **`batchEvictCache(String cacheName, Collection<T> keys)`**: Generic batch eviction for any cache and key type
- **`evictCachesByPattern(String cacheNamePattern)`**: Evicts all caches matching a regex pattern

#### Conditional Operations

- **`conditionalEvictCache(String cacheName, boolean force, Supplier<Boolean> condition)`**: Generic conditional eviction with custom condition
- **`conditionalEvictCache(String cacheName, boolean force)`**: Conditional eviction with default condition
- **`handleDataImportCacheInvalidation(String importType)`**: Handles cache invalidation after data import operations

### 2. Entity-Specific Cache Management

The `ClinicServiceImpl` provides entity-specific cache management methods for all domain entities:

#### Vet Cache Management
- **`conditionalEvictVetsCache(boolean force)`**: Vet-specific conditional eviction
- **`batchEvictVetCache(Collection<Integer> vetIds)`**: Batch eviction for multiple vets
- **`refreshVetCache(int vetId)`**: Refresh specific vet cache entry

#### Owner Cache Management
- **`conditionalEvictOwnersCache(boolean force)`**: Owner-specific conditional eviction
- **`batchEvictOwnerCache(Collection<Integer> ownerIds)`**: Batch eviction for multiple owners
- **`refreshOwnerCache(int ownerId)`**: Refresh specific owner cache entry

#### Pet Cache Management
- **`conditionalEvictPetsCache(boolean force)`**: Pet-specific conditional eviction
- **`batchEvictPetCache(Collection<Integer> petIds)`**: Batch eviction for multiple pets
- **`refreshPetCache(int petId)`**: Refresh specific pet cache entry

#### Visit Cache Management
- **`conditionalEvictVisitsCache(boolean force)`**: Visit-specific conditional eviction
- **`batchEvictVisitCache(Collection<Integer> visitIds)`**: Batch eviction for multiple visits
- **`refreshVisitCache(int visitId)`**: Refresh specific visit cache entry

#### Specialty Cache Management
- **`conditionalEvictSpecialtiesCache(boolean force)`**: Specialty-specific conditional eviction
- **`batchEvictSpecialtyCache(Collection<Integer> specialtyIds)`**: Batch eviction for multiple specialties
- **`refreshSpecialtyCache(int specialtyId)`**: Refresh specific specialty cache entry

#### Pet Type Cache Management
- **`conditionalEvictPetTypesCache(boolean force)`**: Pet type-specific conditional eviction
- **`batchEvictPetTypeCache(Collection<Integer> petTypeIds)`**: Batch eviction for multiple pet types
- **`refreshPetTypeCache(int petTypeId)`**: Refresh specific pet type cache entry

### 3. Cache Annotations

All entity finder methods are automatically cached:

#### Vet Cache Annotations
- **`findVetById(int id)`**: Cached in "vets" cache with vet ID as key
- **`findAllVets()`**: Cached in "vets" cache with "allVets" as key
- **`saveVet(Vet vet)`**: Evicts all entries in "vets" cache
- **`deleteVet(Vet vet)`**: Evicts all entries in "vets" cache

#### Owner Cache Annotations
- **`findOwnerById(int id)`**: Cached in "owners" cache with owner ID as key
- **`findAllOwners()`**: Cached in "owners" cache with "allOwners" as key
- **`saveOwner(Owner owner)`**: Evicts all entries in "owners" cache
- **`deleteOwner(Owner owner)`**: Evicts all entries in "owners" cache

#### Pet Cache Annotations
- **`findPetById(int id)`**: Cached in "pets" cache with pet ID as key
- **`findAllPets()`**: Cached in "pets" cache with "allPets" as key
- **`savePet(Pet pet)`**: Evicts all entries in "pets" cache
- **`deletePet(Pet pet)`**: Evicts all entries in "pets" cache

#### Visit Cache Annotations
- **`findVisitById(int id)`**: Cached in "visits" cache with visit ID as key
- **`findAllVisits()`**: Cached in "visits" cache with "allVisits" as key
- **`saveVisit(Visit visit)`**: Evicts all entries in "visits" cache
- **`deleteVisit(Visit visit)`**: Evicts all entries in "visits" cache

#### Specialty Cache Annotations
- **`findSpecialtyById(int id)`**: Cached in "specialties" cache with specialty ID as key
- **`findAllSpecialties()`**: Cached in "specialties" cache with "allSpecialties" as key
- **`saveSpecialty(Specialty specialty)`**: Evicts all entries in "specialties" cache
- **`deleteSpecialty(Specialty specialty)`**: Evicts all entries in "specialties" cache

#### Pet Type Cache Annotations
- **`findPetTypeById(int id)`**: Cached in "petTypes" cache with pet type ID as key
- **`findAllPetTypes()`**: Cached in "petTypes" cache with "allPetTypes" as key
- **`savePetType(PetType petType)`**: Evicts all entries in "petTypes" cache
- **`deletePetType(PetType petType)`**: Evicts all entries in "petTypes" cache

### 4. Scheduled Cache Invalidation

The `CacheScheduledService` provides optional scheduled cache invalidation that can be enabled via application properties:

```properties
# Enable scheduled cache invalidation
petclinic.cache.scheduled.enabled=true

# Conditional eviction rate (default: 1 hour)
petclinic.cache.scheduled.rate=3600000

# Daily cache refresh cron (default: 2 AM daily)  
petclinic.cache.scheduled.cron=0 0 2 * * ?
```

When enabled, the system automatically:
- Performs conditional cache eviction on all entity caches at regular intervals
- Executes daily cache refresh (full eviction) at 2 AM for all caches

### 5. Audit Logging

All cache operations are logged with detailed information:

- **INFO level**: Successful cache operations with details
- **WARN level**: Failed operations or non-existent cache access attempts
- **DEBUG level**: Cache misses and conditional operation skips

Example log entries:
```
INFO  - Cache eviction completed - Cache: 'vets', Key: '1'
INFO  - Batch eviction completed for vets cache - Individual entries: 5, All vets cleared
WARN  - Attempted to evict non-existent cache: 'invalidCache'
```

## Usage Examples

### Using CacheManagementService Directly

```java
@Autowired
private CacheManagementService cacheManagementService;

// Generic cache eviction - works with any cache
cacheManagementService.evictCache("owners", 123);
cacheManagementService.evictCache("pets", null); // Clear entire cache

// Generic batch eviction
List<Integer> ownerIds = Arrays.asList(1, 2, 3);
cacheManagementService.batchEvictCache("owners", ownerIds);

// Generic cache refresh with custom data loader
Owner refreshedOwner = cacheManagementService.refreshCacheEntry("owners", 1, 
    (id) -> ownerRepository.findById(id));

// Conditional eviction with custom logic
cacheManagementService.conditionalEvictCache("pets", false, () -> shouldEvictPets());

// Pattern-based eviction
cacheManagementService.evictCachesByPattern(".*_temp");
```

### Using Entity-Specific Cache Methods

```java
// Vet cache operations
List<Integer> vetIds = Arrays.asList(1, 2, 3);
clinicService.batchEvictVetCache(vetIds);
clinicService.refreshVetCache(1);
clinicService.conditionalEvictVetsCache(false);

// Owner cache operations
List<Integer> ownerIds = Arrays.asList(1, 2, 3);
clinicService.batchEvictOwnerCache(ownerIds);
clinicService.refreshOwnerCache(1);
clinicService.conditionalEvictOwnersCache(false);

// Pet cache operations
List<Integer> petIds = Arrays.asList(1, 2, 3);
clinicService.batchEvictPetCache(petIds);
clinicService.refreshPetCache(1);
clinicService.conditionalEvictPetsCache(false);

// Similar patterns available for visits, specialties, and pet types
```

### Data Import Scenarios

```java
// After importing specific entity data
cacheManagementService.handleDataImportCacheInvalidation("vets");
cacheManagementService.handleDataImportCacheInvalidation("owners");
cacheManagementService.handleDataImportCacheInvalidation("pets");
cacheManagementService.handleDataImportCacheInvalidation("visits");
cacheManagementService.handleDataImportCacheInvalidation("specialties");
cacheManagementService.handleDataImportCacheInvalidation("pettypes");

// After full data import
cacheManagementService.handleDataImportCacheInvalidation("all");
```

### Cache Refresh

```java
// Refresh specific cache entries for any entity
clinicService.refreshVetCache(1);
clinicService.refreshOwnerCache(1);
clinicService.refreshPetCache(1);
clinicService.refreshVisitCache(1);
clinicService.refreshSpecialtyCache(1);
clinicService.refreshPetTypeCache(1);
```

## Integration with Business Logic

### Data Import Workflows

The cache management system integrates with data import processes:

1. **Pre-import**: No special handling required
2. **Post-import**: Call `handleDataImportCacheInvalidation(importType)` to clear relevant caches
3. **Error handling**: Failed imports don't affect cache state

### External Event Integration

Cache invalidation can be triggered by external events:

```java
// In event handler
@EventListener
public void handleExternalDataChange(ExternalDataChangeEvent event) {
    switch (event.getDataType()) {
        case "vets":
            clinicService.conditionalEvictVetsCache(true);
            break;
        case "owners":
            clinicService.conditionalEvictOwnersCache(true);
            break;
        case "pets":
            clinicService.conditionalEvictPetsCache(true);
            break;
        // Similar cases for visits, specialties, pet types
    }
}
```

## Cache Configuration

### Available Caches

The system defines the following cache constants in `CacheConfig`:

- `VETS_CACHE`: "vets" - For veterinarian data
- `OWNERS_CACHE`: "owners" - For pet owner data
- `PETS_CACHE`: "pets" - For pet data
- `VISITS_CACHE`: "visits" - For visit data
- `SPECIALTIES_CACHE`: "specialties" - For specialty data
- `PET_TYPES_CACHE`: "petTypes" - For pet type data

### Cache Provider

The system uses Spring Boot's default cache configuration. For production environments, consider configuring a distributed cache provider like Redis or Hazelcast.

## Extending Cache Management

### Adding New Cached Methods

1. Add cache annotations to service methods:
```java
@Cacheable(value = "owners", key = "#id")
public Owner findOwnerById(int id) {
    // implementation
}
```

2. Add corresponding eviction logic:
```java
@CacheEvict(value = "owners", allEntries = true)
public void saveOwner(Owner owner) {
    // implementation
}
```

3. Extend programmatic cache methods as needed:
```java
public void evictOwnerCache(int ownerId) {
    evictCache("owners", ownerId);
}
```

### Custom Eviction Policies

Implement custom business logic in conditional eviction methods:

```java
private boolean shouldEvictOwnersCache() {
    // Custom business logic
    return System.currentTimeMillis() % 1000 == 0; // Example
}
```

## Testing

Integration tests are provided in `CacheManagementIntegrationTest` to verify:

- Cache population and eviction behavior
- Batch operations functionality
- Conditional eviction logic
- Pattern-based eviction
- Data import integration
- Error handling for non-existent caches

Run tests with:
```bash
mvn test -Dtest=CacheManagementIntegrationTest
```

## Performance Considerations

- **Cache Size**: Monitor cache memory usage in production
- **Eviction Frequency**: Balance between data freshness and performance
- **Batch Operations**: Use batch eviction for multiple related entities
- **Scheduled Operations**: Configure appropriate intervals based on data change frequency

## Monitoring

Monitor cache performance using:

- Application logs (audit trail)
- Spring Boot Actuator cache metrics (if enabled)
- Custom cache statistics (can be added via CacheManager)

## Troubleshooting

### Common Issues

1. **Cache not working**: Ensure `@EnableCaching` is present and AOP is configured
2. **No eviction logs**: Check logging configuration for the service package
3. **Scheduled tasks not running**: Verify `petclinic.cache.scheduled.enabled=true`

### Debug Mode

Enable debug logging for cache operations:

```properties
logging.level.org.springframework.samples.petclinic.service.ClinicServiceImpl=DEBUG
logging.level.org.springframework.samples.petclinic.service.CacheManagementService=DEBUG
```
