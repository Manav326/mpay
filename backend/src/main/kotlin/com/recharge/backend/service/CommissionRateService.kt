package com.recharge.backend.service

import com.recharge.backend.domain.RoleCommissionRateEntity
import com.recharge.backend.repository.RoleCommissionRateRepository
import com.recharge.backend.repository.UserRepository
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant

@Service
class CommissionRateService(
    private val users: UserRepository,
    private val rates: RoleCommissionRateRepository,
    @Value("\${app.commission.client-percent:1.0}") private val legacyClientPercent: BigDecimal
) {
    fun rateForUser(userId: Long): BigDecimal {
        val user = users.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        return rateForRole(user.role)
    }

    fun rateForRole(role: String): BigDecimal =
        rates.findByRoleIgnoreCaseAndActiveTrue(role).orElse(null)?.commissionPercent?.setScale(2)
            ?: if (role.equals("CLIENT", true)) legacyClientPercent.setScale(2) else BigDecimal.ZERO.setScale(2)

    fun allRates(): List<RoleCommissionRateEntity> = rates.findAllByOrderByRoleAsc()

    @Transactional
    fun upsert(role: String, percent: BigDecimal, active: Boolean): RoleCommissionRateEntity {
        require(percent >= BigDecimal.ZERO && percent < BigDecimal(100)) { "Commission percent must be between 0 and 100" }
        val normalized = role.trim().uppercase()
        require(normalized in setOf("CLIENT", "MANAGER", "ADMIN")) { "Unsupported role" }
        val entity = rates.findByRoleIgnoreCase(normalized).orElseGet { RoleCommissionRateEntity(role = normalized) }
        entity.role = normalized
        entity.commissionPercent = percent.setScale(4)
        entity.active = active
        entity.updatedAt = Instant.now()
        return rates.save(entity)
    }
}
