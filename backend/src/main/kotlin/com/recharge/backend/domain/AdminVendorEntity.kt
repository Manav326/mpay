package com.recharge.backend.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "admin_vendors")
class AdminVendorEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, length = 160) var name: String = "",
    @Column(nullable = false, length = 40) var category: String = "SERVICES",
    @Column(nullable = false, length = 120) var city: String = "",
    @Column(nullable = false, length = 30) var phone: String = "",
    @Column(name = "commission_rate", nullable = false, precision = 7, scale = 2) var commissionRate: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false) var active: Boolean = true,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)
