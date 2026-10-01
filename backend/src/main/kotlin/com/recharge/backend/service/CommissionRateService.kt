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
        require(user.role.equals("CLIENT", true)) { "Commission rates apply only to client accounts" }
        return rateForRole(user.role)
    }

    fun rateForRole(role: String): BigDecimal {
        if (!role.equals("CLIENT", true)) return BigDecimal.ZERO.setScale(2)
        return rates.findByRoleIgnoreCaseAndActiveTrue("CLIENT").orElse(null)?.commissionPercent?.setScale(2)
            ?: legacyClientPercent.setScale(2)
    }

    fun allRates(): List<RoleCommissionRateEntity> =
        rates.findAllByRoleAscIfSupported()
            .filter { it.role.equals("CLIENT", true) }
}
