package org.springframework.samples.petclinic.security;

import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.stereotype.Service;

@Service("securityService")
public class SecurityService {

    private final ClinicService clinicService;

    public SecurityService(ClinicService clinicService) {
        this.clinicService = clinicService;
    }

    public boolean isOwner(String username, int ownerId) {
        if (username == null) return false;
        Owner owner = clinicService.findOwnerById(ownerId);
        return owner != null && username.equals(owner.getUsername());
    }

    public boolean isVet(String username, int vetId) {
        if (username == null) return false;
        Vet vet = clinicService.findVetById(vetId);
        return vet != null && username.equals(vet.getUsername());
    }

    public boolean isPetOwner(String username, int petId) {
        if (username == null) return false;
        Pet pet = clinicService.findPetById(petId);
        return pet != null && pet.getOwner() != null && username.equals(pet.getOwner().getUsername());
    }

    public boolean isVisitOwner(String username, int visitId) {
        if (username == null) return false;
        Visit visit = clinicService.findVisitById(visitId);
        return visit != null && visit.getPet() != null && visit.getPet().getOwner() != null &&
               username.equals(visit.getPet().getOwner().getUsername());
    }
}
