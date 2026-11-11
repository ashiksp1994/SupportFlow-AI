package com.supportflow.customers;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.supportflow.audit.AuditService;
import com.supportflow.identity.security.AccessControlService;
import com.supportflow.organisations.Organisation;
import com.supportflow.organisations.OrganisationService;

@Service
public class CustomerService {

    public record CreateCustomerRequest(
            @NotBlank(message = "Full name is required") String fullName,
            @NotBlank(message = "Email is required") @Email String email,
            String phone,
            String jobTitle,
            Long organisationId,
            Boolean vip
    ) {}

    private final CustomerRepository customerRepository;
    private final OrganisationService organisationService;
    private final AuditService auditService;
    private final AccessControlService accessControlService;

    public CustomerService(
            CustomerRepository customerRepository,
            OrganisationService organisationService,
            AuditService auditService,
            AccessControlService accessControlService) {
        this.customerRepository = customerRepository;
        this.organisationService = organisationService;
        this.auditService = auditService;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public List<CustomerProfile> findAll() {
        AccessControlService.CallerContext caller = accessControlService.requireAuthenticated();
        if (!caller.isAdmin() && caller.organisationId() != null) {
            return customerRepository.findByOrganisationId(caller.organisationId());
        }
        if (caller.isCustomer()) {
            return customerRepository.findAll().stream()
                    .filter(c -> accessControlService.canAccessTenant(c.getOrganisationId(), c.getEmail()))
                    .toList();
        }
        return customerRepository.findAll();
    }

    @Transactional(readOnly = true)
    public CustomerProfile findById(Long id) {
        CustomerProfile customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + id));
        accessControlService.verifyTenantAccess(customer.getOrganisationId(), customer.getEmail());
        return customer;
    }

    @Transactional
    public CustomerProfile createCustomer(CreateCustomerRequest request, String actor) {
        accessControlService.requireStaff();
        CustomerProfile customer = new CustomerProfile();
        customer.setFullName(request.fullName().trim());
        customer.setEmail(request.email().trim().toLowerCase());
        customer.setPhone(request.phone());
        customer.setJobTitle(request.jobTitle());
        customer.setVip(Boolean.TRUE.equals(request.vip()));

        if (request.organisationId() != null) {
            Organisation org = organisationService.findById(request.organisationId());
            customer.setOrganisationId(org.getId());
            customer.setOrganisationName(org.getName());
        }

        CustomerProfile saved = customerRepository.save(customer);
        auditService.recordEvent("CUSTOMER_CREATED", actor, "CUSTOMER", String.valueOf(saved.getId()),
                "Created customer profile for " + saved.getEmail());
        return saved;
    }
}
