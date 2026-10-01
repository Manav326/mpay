package com.recharge.backend.api

import com.recharge.backend.domain.EmployeeEntity
import com.recharge.backend.repository.EmployeeRepository
import com.recharge.backend.service.ClientCommissionService
import com.recharge.backend.service.RoleAccessService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin/commission-settings")
class ClientCommissionAdminController(
    private val clientCommissionService: ClientCommissionService,
    private val employees: EmployeeRepository,
    private val roleAccess: RoleAccessService
) {
    private fun currentEmployee(authentication: Authentication): EmployeeEntity =
        authentication.name.toLongOrNull()?.let {
            employees.findById(it).orElseThrow { IllegalArgumentException("Employee not found") }
        } ?: throw IllegalStateException("Invalid authenticated employee")

    private fun requireCommissionPermission(authentication: Authentication) =
        roleAccess.requirePermission(currentEmployee(authentication), "MANAGE_COMMISSION_RATES")

    @GetMapping
    fun get(authentication: Authentication): ClientCommissionSettingsResponse {
        requireCommissionPermission(authentication)
        return clientCommissionService.settingsResponse()
    }

    @PutMapping
    fun update(
        authentication: Authentication,
        @Valid @RequestBody request: UpdateClientCommissionSettingsRequest
    ): ClientCommissionSettingsResponse {
        requireCommissionPermission(authentication)
        return clientCommissionService.updateSettings(
            level2DirectClientThreshold = request.level2DirectClientThreshold,
            upstreamCommissionPercent = request.upstreamCommissionPercent,
            upstreamCommissionActive = request.upstreamCommissionActive
        )
    }
}
