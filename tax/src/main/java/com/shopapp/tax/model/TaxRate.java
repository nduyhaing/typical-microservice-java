package com.shopapp.tax.model;

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
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tax_rate")
@Getter
@Setter
@NoArgsConstructor
public class TaxRate extends AbstractAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Percentage, 0..100 with up to 4 decimals (YAS: double). */
    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal rate;

    /** Trimmed; null means "any zip code". */
    @Column(length = 25)
    private String zipCode;

    /** Null means "any state or province of the country". */
    @Column
    private Long stateOrProvinceId;

    @Column(nullable = false)
    private Long countryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_class_id", nullable = false)
    private TaxClass taxClass;
}
