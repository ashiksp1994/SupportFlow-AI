package com.supportflow.organisations;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.supportflow.audit.AuditService;

@Service
public class OrganisationService {

    public record CreateOrganisationRequest(
            @NotBlank(message = "Organisation name is required") String name,
            @NotBlank(message = "Domain is required") String domain,
            String slaTier
    ) {}

    private final OrganisationRepository organisationRepository;
    private final AuditService auditService;

    public OrganisationService(OrganisationRepository organisationRepository, AuditService auditService) {
        this.organisationRepository = organisationRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Organisation> findAll() {
        return organisationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Organisation findById(Long id) {
        return organisationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation not found: " + id));
    }

    @Transactional
    public Organisation createOrganisation(CreateOrganisationRequest request, String actor) {
        Organisation org = new Organisation();
        org.setName(request.name().trim());
        org.setDomain(request.domain().trim().toLowerCase());
        String tier = request.slaTier() != null ? request.slaTier().trim().toUpperCase() : "STANDARD";
        org.setSlaTier(tier);
        switch (tier) {
            case "ENTERPRISE" -> {
                org.setResponseSlaMinutes(15);
                org.setResolutionSlaHours(4);
            }
            case "BUSINESS" -> {
                org.setResponseSlaMinutes(60);
                org.setResolutionSlaHours(12);
            }
            default -> {
                org.setResponseSlaMinutes(120);
                org.setResolutionSlaHours(24);
            }
        }
        Organisation saved = organisationRepository.save(org);
        auditService.recordEvent("ORGANISATION_CREATED", actor, "ORGANISATION", String.valueOf(saved.getId()),
                "Created organisation " + saved.getName() + " (" + saved.getSlaTier() + ")");
        return saved;
    }

    @RestController
    @RequestMapping("/api/organisations")
    public static class OrganisationController {

        private final OrganisationService organisationService;

        public OrganisationController(OrganisationService organisationService) {
            this.organisationService = organisationService;
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
        public Organisation createOrganisation(
                @Valid @RequestBody CreateOrganisationRequest request,
                org.springframework.security.core.Authentication auth) {
            String actor = auth != null ? auth.getName() : "SYSTEM";
            return organisationService.createOrganisation(request, actor);
        }
    }
}
