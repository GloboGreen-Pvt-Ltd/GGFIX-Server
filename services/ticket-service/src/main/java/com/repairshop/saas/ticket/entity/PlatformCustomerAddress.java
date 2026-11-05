package com.repairshop.saas.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Read/write view of the platform-wide customer_addresses table. ticket-service
 * CREATES a row here when the shop owner adds a new customer with an address —
 * tagged as the customer's default home so the customer app prefills it.
 */
@Entity
@Table(name = "customer_addresses")
@Getter
@Setter
@NoArgsConstructor
public class PlatformCustomerAddress {

    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_user_id")
    private UUID customerUserId;

    @Column(name = "pincode")
    private String pincode;

    @Column(name = "locality")
    private String locality;

    @Column(name = "area")
    private String area;

    @Column(name = "address_line")
    private String addressLine;

    @Column(name = "city")
    private String city;

    @Column(name = "district")
    private String district;

    @Column(name = "taluk")
    private String taluk;

    @Column(name = "state")
    private String state;

    @Column(name = "is_default")
    private Boolean isDefault;
}
