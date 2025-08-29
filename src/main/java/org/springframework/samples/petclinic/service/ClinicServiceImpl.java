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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.samples.petclinic.cache.CacheManagementService;
import org.springframework.samples.petclinic.config.CacheConfig;
import org.springframework.samples.petclinic.model.*;
import org.springframework.samples.petclinic.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Mostly used as a facade for all Petclinic controllers
 * Also a placeholder for @Transactional and @Cacheable annotations
 *
 * @author Michael Isvy
 * @author Vitaliy Fedoriv
 */
@Service
public class ClinicServiceImpl implements ClinicService {

    private static final Logger logger = LoggerFactory.getLogger(ClinicServiceImpl.class);

    private final PetRepository petRepository;
    private final VetRepository vetRepository;
    private final OwnerRepository ownerRepository;
    private final VisitRepository visitRepository;
    private final SpecialtyRepository specialtyRepository;
    private final PetTypeRepository petTypeRepository;
    private final CacheManagementService cacheManagementService;

    @Autowired
    public ClinicServiceImpl(
        PetRepository petRepository,
        VetRepository vetRepository,
        OwnerRepository ownerRepository,
        VisitRepository visitRepository,
        SpecialtyRepository specialtyRepository,
        PetTypeRepository petTypeRepository,
        CacheManagementService cacheManagementService) {
        this.petRepository = petRepository;
        this.vetRepository = vetRepository;
        this.ownerRepository = ownerRepository;
        this.visitRepository = visitRepository;
        this.specialtyRepository = specialtyRepository;
        this.petTypeRepository = petTypeRepository;
        this.cacheManagementService = cacheManagementService;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.PETS_CACHE, key = "'allPets'")
    public Collection<Pet> findAllPets() throws DataAccessException {
        logger.debug("Fetching all pets from repository - cache miss");
        return petRepository.findAll();
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.PETS_CACHE, allEntries = true)
    public void deletePet(Pet pet) throws DataAccessException {
        petRepository.delete(pet);
        logger.info("Deleted pet with id {} - cleared all pets cache", pet.getId());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.VISITS_CACHE, key = "#visitId")
    public Visit findVisitById(int visitId) throws DataAccessException {
        logger.debug("Fetching visit with id {} from repository - cache miss", visitId);
        return findEntityById(() -> visitRepository.findById(visitId));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.VISITS_CACHE, key = "'allVisits'")
    public Collection<Visit> findAllVisits() throws DataAccessException {
        logger.debug("Fetching all visits from repository - cache miss");
        return visitRepository.findAll();
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.VISITS_CACHE, allEntries = true)
    public void deleteVisit(Visit visit) throws DataAccessException {
        visitRepository.delete(visit);
        logger.info("Deleted visit with id {} - cleared all visits cache", visit.getId());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.VETS_CACHE, key = "#id")
    public Vet findVetById(int id) throws DataAccessException {
        logger.debug("Fetching vet with id {} from repository - cache miss", id);
        return findEntityById(() -> vetRepository.findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.VETS_CACHE, key = "'allVets'")
    public Collection<Vet> findAllVets() throws DataAccessException {
        logger.debug("Fetching all vets from repository - cache miss");
        return vetRepository.findAll();
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.VETS_CACHE, allEntries = true)
    public void saveVet(Vet vet) throws DataAccessException {
        vetRepository.save(vet);
        logger.info("Saved vet with id {} - cleared all vets cache", vet.getId());
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.VETS_CACHE, allEntries = true)
    public void deleteVet(Vet vet) throws DataAccessException {
        vetRepository.delete(vet);
        logger.info("Deleted vet with id {} - cleared all vets cache", vet.getId());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.OWNERS_CACHE, key = "'allOwners'")
    public Collection<Owner> findAllOwners() throws DataAccessException {
        logger.debug("Fetching all owners from repository - cache miss");
        return ownerRepository.findAll();
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.OWNERS_CACHE, allEntries = true)
    public void deleteOwner(Owner owner) throws DataAccessException {
        ownerRepository.delete(owner);
        logger.info("Deleted owner with id {} - cleared all owners cache", owner.getId());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.PET_TYPES_CACHE, key = "#petTypeId")
    public PetType findPetTypeById(int petTypeId) {
        logger.debug("Fetching pet type with id {} from repository - cache miss", petTypeId);
        return findEntityById(() -> petTypeRepository.findById(petTypeId));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.PET_TYPES_CACHE, key = "'allPetTypes'")
    public Collection<PetType> findAllPetTypes() throws DataAccessException {
        logger.debug("Fetching all pet types from repository - cache miss");
        return petTypeRepository.findAll();
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.PET_TYPES_CACHE, allEntries = true)
    public void savePetType(PetType petType) throws DataAccessException {
        petTypeRepository.save(petType);
        logger.info("Saved pet type with id {} - cleared all pet types cache", petType.getId());
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.PET_TYPES_CACHE, allEntries = true)
    public void deletePetType(PetType petType) throws DataAccessException {
        petTypeRepository.delete(petType);
        logger.info("Deleted pet type with id {} - cleared all pet types cache", petType.getId());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.SPECIALTIES_CACHE, key = "#specialtyId")
    public Specialty findSpecialtyById(int specialtyId) {
        logger.debug("Fetching specialty with id {} from repository - cache miss", specialtyId);
        return findEntityById(() -> specialtyRepository.findById(specialtyId));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.SPECIALTIES_CACHE, key = "'allSpecialties'")
    public Collection<Specialty> findAllSpecialties() throws DataAccessException {
        logger.debug("Fetching all specialties from repository - cache miss");
        return specialtyRepository.findAll();
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.SPECIALTIES_CACHE, allEntries = true)
    public void saveSpecialty(Specialty specialty) throws DataAccessException {
        specialtyRepository.save(specialty);
        logger.info("Saved specialty with id {} - cleared all specialties cache", specialty.getId());
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.SPECIALTIES_CACHE, allEntries = true)
    public void deleteSpecialty(Specialty specialty) throws DataAccessException {
        specialtyRepository.delete(specialty);
        logger.info("Deleted specialty with id {} - cleared all specialties cache", specialty.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<PetType> findPetTypes() throws DataAccessException {
        return petRepository.findPetTypes();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.OWNERS_CACHE, key = "#id")
    public Owner findOwnerById(int id) throws DataAccessException {
        logger.debug("Fetching owner with id {} from repository - cache miss", id);
        return findEntityById(() -> ownerRepository.findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.PETS_CACHE, key = "#id")
    public Pet findPetById(int id) throws DataAccessException {
        logger.debug("Fetching pet with id {} from repository - cache miss", id);
        return findEntityById(() -> petRepository.findById(id));
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.PETS_CACHE, allEntries = true)
    public void savePet(Pet pet) throws DataAccessException {
        pet.setType(findPetTypeById(pet.getType().getId()));
        petRepository.save(pet);
        logger.info("Saved pet with id {} - cleared all pets cache", pet.getId());
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.VISITS_CACHE, allEntries = true)
    public void saveVisit(Visit visit) throws DataAccessException {
        visitRepository.save(visit);
        logger.info("Saved visit with id {} - cleared all visits cache", visit.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<Vet> findVets() throws DataAccessException {
        return vetRepository.findAll();
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.OWNERS_CACHE, allEntries = true)
    public void saveOwner(Owner owner) throws DataAccessException {
        ownerRepository.save(owner);
        logger.info("Saved owner with id {} - cleared all owners cache", owner.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<Owner> findOwnerByLastName(String lastName) throws DataAccessException {
        return ownerRepository.findByLastName(lastName);
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<Visit> findVisitsByPetId(int petId) {
        return visitRepository.findByPetId(petId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Specialty> findSpecialtiesByNameIn(Set<String> names) {
        return findEntityById(() -> specialtyRepository.findSpecialtiesByNameIn(names));
    }

    private <T> T findEntityById(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (ObjectRetrievalFailureException | EmptyResultDataAccessException e) {
            // Just ignore not found exceptions for Jdbc/Jpa realization
            return null;
        }
    }

    public void conditionalEvictVetsCache(boolean force) {
        cacheManagementService.conditionalEvictCache(CacheConfig.VETS_CACHE, force, this::shouldEvictVetsCache);
    }

    private boolean shouldEvictVetsCache() {
        return true;
    }

    public void batchEvictVetCache(Collection<Integer> vetIds) {
        cacheManagementService.batchEvictCache(CacheConfig.VETS_CACHE, vetIds);
        cacheManagementService.evictCache(CacheConfig.VETS_CACHE, "allVets");
    }

    public void refreshVetCache(int vetId) {
        cacheManagementService.refreshCacheEntry(CacheConfig.VETS_CACHE, vetId, this::findVetById);
    }

    public void conditionalEvictOwnersCache(boolean force) {
        cacheManagementService.conditionalEvictCache(CacheConfig.OWNERS_CACHE, force, this::shouldEvictOwnersCache);
    }

    private boolean shouldEvictOwnersCache() {
        return true;
    }

    public void batchEvictOwnerCache(Collection<Integer> ownerIds) {
        cacheManagementService.batchEvictCache(CacheConfig.OWNERS_CACHE, ownerIds);
        cacheManagementService.evictCache(CacheConfig.OWNERS_CACHE, "allOwners");
    }

    public void refreshOwnerCache(int ownerId) {
        cacheManagementService.refreshCacheEntry(CacheConfig.OWNERS_CACHE, ownerId, this::findOwnerById);
    }

    public void conditionalEvictPetsCache(boolean force) {
        cacheManagementService.conditionalEvictCache(CacheConfig.PETS_CACHE, force, this::shouldEvictPetsCache);
    }

    private boolean shouldEvictPetsCache() {
        return true;
    }

    public void batchEvictPetCache(Collection<Integer> petIds) {
        cacheManagementService.batchEvictCache(CacheConfig.PETS_CACHE, petIds);
        cacheManagementService.evictCache(CacheConfig.PETS_CACHE, "allPets");
    }

    public void refreshPetCache(int petId) {
        cacheManagementService.refreshCacheEntry(CacheConfig.PETS_CACHE, petId, this::findPetById);
    }

    public void conditionalEvictVisitsCache(boolean force) {
        cacheManagementService.conditionalEvictCache(CacheConfig.VISITS_CACHE, force, this::shouldEvictVisitsCache);
    }

    private boolean shouldEvictVisitsCache() {
        return true;
    }

    public void batchEvictVisitCache(Collection<Integer> visitIds) {
        cacheManagementService.batchEvictCache(CacheConfig.VISITS_CACHE, visitIds);
        cacheManagementService.evictCache(CacheConfig.VISITS_CACHE, "allVisits");
    }

    public void refreshVisitCache(int visitId) {
        cacheManagementService.refreshCacheEntry(CacheConfig.VISITS_CACHE, visitId, this::findVisitById);
    }

    public void conditionalEvictSpecialtiesCache(boolean force) {
        cacheManagementService.conditionalEvictCache(CacheConfig.SPECIALTIES_CACHE, force, this::shouldEvictSpecialtiesCache);
    }

    private boolean shouldEvictSpecialtiesCache() {
        return true;
    }

    public void batchEvictSpecialtyCache(Collection<Integer> specialtyIds) {
        cacheManagementService.batchEvictCache(CacheConfig.SPECIALTIES_CACHE, specialtyIds);
        cacheManagementService.evictCache(CacheConfig.SPECIALTIES_CACHE, "allSpecialties");
    }

    public void refreshSpecialtyCache(int specialtyId) {
        cacheManagementService.refreshCacheEntry(CacheConfig.SPECIALTIES_CACHE, specialtyId, this::findSpecialtyById);
    }

    public void conditionalEvictPetTypesCache(boolean force) {
        cacheManagementService.conditionalEvictCache(CacheConfig.PET_TYPES_CACHE, force, this::shouldEvictPetTypesCache);
    }

    private boolean shouldEvictPetTypesCache() {
        return true;
    }

    public void batchEvictPetTypeCache(Collection<Integer> petTypeIds) {
        cacheManagementService.batchEvictCache(CacheConfig.PET_TYPES_CACHE, petTypeIds);
        cacheManagementService.evictCache(CacheConfig.PET_TYPES_CACHE, "allPetTypes");
    }

    public void refreshPetTypeCache(int petTypeId) {
        cacheManagementService.refreshCacheEntry(CacheConfig.PET_TYPES_CACHE, petTypeId, this::findPetTypeById);
    }
}
