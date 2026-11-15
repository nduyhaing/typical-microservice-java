package com.shopapp.location.model;

import com.shopapp.commonlibrary.model.AbstractAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A postal address. {@code createdBy} (the creating user's Keycloak id) is its owner.
 */
@Entity
@Table(name = "address")
@Getter
@Setter
@NoArgsConstructor
public class Address extends AbstractAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 450)
    private String contactName;

    @Column(length = 25)
    private String phone;

    @Column(length = 450, name = "address_line_1")
    private String addressLine1;

    @Column(length = 450, name = "address_line_2")
    private String addressLine2;

    @Column(length = 450)
    private String city;

    @Column(length = 25)
    private String zipCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_or_province_id")
    private StateOrProvince stateOrProvince;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;
}
