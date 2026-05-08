package com.pim.ecommerce.domain.entity;

import com.pim.ecommerce.domain.entity.enums.PaymentMethod;
import com.pim.ecommerce.domain.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column
    private Integer installments;

    @Column(length = 4)
    private String cardLastFourDigits;

    @Column(columnDefinition = "TEXT")
    private String pixCode;

    @Column(columnDefinition = "TEXT")
    private String pixQrCodeBase64;

    @Column(length = 255)
    private String pixConfirmationUrl;

    @Column(length = 255)
    private String bankSlipBarCode;

    @Column(columnDefinition = "TEXT")
    private String bankSlipBarCodeBase64;

    @Column(length = 255)
    private String bankSlipConfirmationUrl;

    @Column(length = 255)
    private String digitableLine;

    @Column(length = 255)
    private String notes;

    @Column(length = 100)
    private String confirmationToken;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime confirmedAt;

    @Column
    private LocalDateTime cancelledAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;

        normalizeDefaults();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();

        normalizeDefaults();
    }

    private void normalizeDefaults() {
        if (this.status == null) {
            this.status = PaymentStatus.PENDING;
        }
    }
}