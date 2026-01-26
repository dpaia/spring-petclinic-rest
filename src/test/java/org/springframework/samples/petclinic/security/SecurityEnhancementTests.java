package org.springframework.samples.petclinic.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.model.*;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
public class SecurityEnhancementTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private ClinicService clinicService;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(username = "owner1", roles = "OWNER")
    void testOwnerCanAccessOwnProfile() throws Exception {
        Owner owner = new Owner();
        owner.setId(1);
        owner.setFirstName("George");
        owner.setLastName("Franklin");
        owner.setAddress("110 W. Liberty St.");
        owner.setCity("Madison");
        owner.setTelephone("6085551023");
        owner.setUsername("owner1");

        given(clinicService.findOwnerById(1)).willReturn(owner);

        mockMvc.perform(get("/api/owners/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "owner2", roles = "OWNER")
    void testOwnerCannotAccessOtherProfile() throws Exception {
        Owner owner = new Owner();
        owner.setId(1);
        owner.setUsername("owner1");

        given(clinicService.findOwnerById(1)).willReturn(owner);

        mockMvc.perform(get("/api/owners/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testOwnerAdminCanAccessAnyProfile() throws Exception {
        Owner owner = new Owner();
        owner.setId(1);
        owner.setUsername("owner1");

        given(clinicService.findOwnerById(1)).willReturn(owner);

        mockMvc.perform(get("/api/owners/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "vet1", roles = "VET")
    void testVetCanAccessOwnProfile() throws Exception {
        Vet vet = new Vet();
        vet.setId(1);
        vet.setUsername("vet1");
        vet.setFirstName("Helen");
        vet.setLastName("Leary");

        given(clinicService.findVetById(1)).willReturn(vet);

        mockMvc.perform(get("/api/vets/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "vet2", roles = "VET")
    void testVetCannotAccessOtherProfile() throws Exception {
        Vet vet = new Vet();
        vet.setId(1);
        vet.setUsername("vet1");

        given(clinicService.findVetById(1)).willReturn(vet);

        mockMvc.perform(get("/api/vets/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void testUserCanReadPetTypes() throws Exception {
        given(clinicService.findAllPetTypes()).willReturn(java.util.Collections.singletonList(new PetType()));
        mockMvc.perform(get("/api/pettypes"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void testUserCanGetPetType() throws Exception {
        PetType petType = new PetType();
        petType.setId(1);
        petType.setName("dog");
        given(clinicService.findPetTypeById(1)).willReturn(petType);
        mockMvc.perform(get("/api/pettypes/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void testUserCannotAccessOwners() throws Exception {
        mockMvc.perform(get("/api/owners"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner1", roles = "OWNER")
    void testOwnerCannotModifyOtherPet() throws Exception {
        Owner otherOwner = new Owner();
        otherOwner.setId(2);
        otherOwner.setUsername("owner2");

        Pet otherPet = new Pet();
        otherPet.setId(100);
        otherPet.setOwner(otherOwner);

        given(clinicService.findPetById(100)).willReturn(otherPet);

        mockMvc.perform(put("/api/pets/100")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Buddy\",\"birthDate\":\"2020-01-01\",\"type\":{\"id\":1,\"name\":\"dog\"}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner1", roles = "OWNER")
    void testOwnerCannotModifyOtherVisit() throws Exception {
        Owner otherOwner = new Owner();
        otherOwner.setId(2);
        otherOwner.setUsername("owner2");

        Pet otherPet = new Pet();
        otherPet.setId(100);
        otherPet.setOwner(otherOwner);

        Visit otherVisit = new Visit();
        otherVisit.setId(200);
        otherVisit.setPet(otherPet);

        given(clinicService.findVisitById(200)).willReturn(otherVisit);

        mockMvc.perform(put("/api/visits/200")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2023-01-01\",\"description\":\"checkup\"}"))
                .andExpect(status().isForbidden());
    }
}
