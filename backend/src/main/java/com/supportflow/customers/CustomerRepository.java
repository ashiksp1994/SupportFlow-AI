package com.supportflow.customers;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<CustomerProfile, Long> {
    Optional<CustomerProfile> findByEmail(String email);
    List<CustomerProfile> findByOrganisationId(Long organisationId);
}
