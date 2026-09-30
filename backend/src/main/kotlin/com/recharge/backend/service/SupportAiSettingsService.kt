package com.recharge.backend.service

import com.recharge.backend.api.SupportAiSettingsResponse
import com.recharge.backend.config.SupportAiProperties
import com.recharge.backend.domain.AppSettingEntity
import com.recharge.backend.domain.EmployeeEntity
import com.recharge.backend.repository.AppSettingRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class SupportAiSettingsService(
    private val settings: AppSettingRepository,
    private val properties: SupportAiProperties,
    private val roleAccess: RoleAccessService,
    private val employeeAudit: EmployeeAuditService
) {
    companion object {
        const val MANAGE_SUPPORT_AI = "MANAGE_SUPPORT_AI"
        private const val AI_ENABLED_KEY = "SUPPORT_AI_ENABLED"
    }

    fun isEnabled(): Boolean =
        settings.findById(AI_ENABLED_KEY).orElse(null)?.booleanValue ?: false

    fun current(): SupportAiSettingsResponse =
        SupportAiSettingsResponse(
            enabled = isEnabled(),
            providerConfigured = properties.providerConfigured,
            vectorStoreConfigured = properties.vectorStoreId.isNotBlank(),
            model = properties.model
        )

    @Transactional
    fun update(viewer: EmployeeEntity, enabled: Boolean): SupportAiSettingsResponse {
        roleAccess.requirePermission(viewer, MANAGE_SUPPORT_AI)

        if (enabled && !properties.providerConfigured) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Customer Care AI provider is not configured on the backend"
            )
        }

        val now = Instant.now()
        val setting = settings.findById(AI_ENABLED_KEY).orElseGet {
            AppSettingEntity(key = AI_ENABLED_KEY)
        }
        setting.booleanValue = enabled
        setting.updatedByUserId = viewer.id
        setting.updatedAt = now
        settings.save(setting)
        employeeAudit.record(
            actor = viewer,
            action = "SUPPORT_AI_SETTING_CHANGED",
            subjectType = "SUPPORT_AI",
            subjectId = AI_ENABLED_KEY,
            summary = if (enabled) "Enabled Customer Care AI." else "Disabled Customer Care AI.",
            metadata = mapOf(
                "enabled" to enabled,
                "providerConfigured" to properties.providerConfigured,
                "model" to properties.model
            )
        )
        return current()
    }
}
