package com.supportflow.customers;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
            boolean vip
    ) {}

    private final CustomerRepository customerRepository;
    private final OrganisationService organisationService;
    private final AuditService auditService;

    public CustomerService(
            CustomerRepository customerRepository,
            OrganisationService organisationService,
            AuditService auditService) {
        this.customerRepository = customerRepository;
        this.organisationService = organisationService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CustomerProfile> findAll() {
        return customerRepository.findAll();
    }

    @Transactional(readOnly = true)
    public CustomerProfile findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + id));
    }

    @Transactional
    public CustomerProfile createCustomer(CreateCustomerRequest request, String actor) {
        CustomerProfile customer = new CustomerProfile();
        customer.setFullName(request.fullName().trim());
        customer.setEmail(request.email().trim().toLowerCase());
        customer.setPhone(request.phone());
        customer.setJobTitle(request.jobTitle());
        customer.setVip(request.vip());

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

    @RestController
    @RequestMapping("/api/customers")
    public static class CustomerController {

        private final CustomerService customerService;

        public CustomerController(CustomerService customerService) {
            this.customerService = customerService;
        }

        @GetMapping
        public List<CustomerProfile> listCustomers() {
            return customerService.findAll();
        }

        @GetMapping("/{id}")
        public CustomerProfile getCustomer(@PathVariable Long id) {
            return customerService.findById(id);
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public CustomerProfile createCustomer(@Valid @RequestBody CreateCustomerRequest request, Authentication auth) {
            String actor = auth != null ? auth.getName() : "SYSTEM";
            return customerService.createCustomer(request, actor);
        }
    }
}
