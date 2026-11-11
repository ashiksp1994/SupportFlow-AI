package com.supportflow.organisations;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.supportflow.audit.AuditService;
import com.supportflow.identity.security.AccessControlService;

@Service
public class OrganisationService {

    public record CreateOrganisationRequest(
            @NotBlank(message = "Organisation name is required") String name,
            @NotBlank(message = "Domain is required") String domain,
            String slaTier
    ) {}

    private final OrganisationRepository organisationRepository;
    private final AuditService auditService;
    private final AccessControlService accessControlService;

    public OrganisationService(
            OrganisationRepository organisationRepository,
            AuditService auditService,
            AccessControlService accessControlService) {
        this.organisationRepository = organisationRepository;
        this.auditService = auditService;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public List<Organisation> findAll() {
        AccessControlService.CallerContext caller = accessControlService.requireAuthenticated();
        if (!caller.isAdmin() && caller.organisationId() != null) {
            return organisationRepository.findById(caller.organisationId())
                    .map(List::of)
                    .orElse(List.of());
        }
        if (caller.isCustomer()) {
            return List.of();
        }
        return organisationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Organisation findById(Long id) {
        Organisation org = organisationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation not found: " + id));
        accessControlService.verifyTenantAccess(org.getId(), null);
        return org;
    }

    @Transactional
    public Organisation createOrganisation(CreateOrganisationRequest request, String actor) {
        accessControlService.requireStaff();
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
}
