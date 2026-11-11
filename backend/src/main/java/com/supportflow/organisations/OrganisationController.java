package com.supportflow.organisations;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.supportflow.identity.security.AccessControlService;

@RestController
@RequestMapping("/api/organisations")
public class OrganisationController {

    private final OrganisationService organisationService;
    private final AccessControlService accessControlService;

    public OrganisationController(OrganisationService organisationService, AccessControlService accessControlService) {
        this.organisationService = organisationService;
        this.accessControlService = accessControlService;
    }

    @GetMapping
    public List<Organisation> listOrganisations() {
        return organisationService.findAll();
    }

    @GetMapping("/{id}")
    public Organisation getOrganisation(@PathVariable Long id) {
        return organisationService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public Organisation createOrganisation(
            @Valid @RequestBody OrganisationService.CreateOrganisationRequest request,
            Authentication auth) {
        String actor = accessControlService.requireActorUsername(auth);
        return organisationService.createOrganisation(request, actor);
    }
}
