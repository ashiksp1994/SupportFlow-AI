package com.supportflow.customers;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "customers")
public class CustomerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "organisation_id")
    private Long organisationId;

    @Column(name = "organisation_name")
    private String organisationName;

    @Column(nullable = false)
    private boolean vip = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
