package com.recharge.backend.service

import com.recharge.backend.api.VoiceCallRoleAccessResponse
import com.recharge.backend.api.VoiceCallUserAccessResponse
import com.recharge.backend.domain.EmployeeEntity
import com.recharge.backend.domain.EmployeePermissionOverrideEntity
import com.recharge.backend.domain.RolePermissionEntity
import com.recharge.backend.repository.EmployeePermissionOverrideRepository
import com.recharge.backend.repository.EmployeeRepository
import com.recharge.backend.repository.RolePermissionRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class VoiceCallAccessService(
    private val roleAccess: RoleAccessService,
    private val rolePermissions: RolePermissionRepository,
    private val overrides: EmployeePermissionOverrideRepository,
    private val employees: EmployeeRepository,
    private val voiceCalls: VoiceCallService,
    private val audit: EmployeeAuditService
) {
    fun roleAccess(viewer: EmployeeEntity): List<VoiceCallRoleAccessResponse> {
        roleAccess.requirePermission(viewer, "MANAGE_CALL_ACCESS")
        return roleAccess.portalRoles()
            .filterNot { it.equals("CLIENT", true) }
            .map { role ->
                VoiceCallRoleAccessResponse(role.uppercase(), roleAccess.hasRolePermission(role, "CALL_CUSTOMER"))
            }
            .sortedBy { it.role }
    }

    fun setRoleAccess(viewer: EmployeeEntity, role: String, enabled: Boolean): VoiceCallRoleAccessResponse {
        roleAccess.requirePermission(viewer, "MANAGE_CALL_ACCESS")
        val normalized = role.trim().uppercase()
        if (normalized.isBlank() || normalized.equals("CLIENT", true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Voice calling can only be assigned to employee roles")
        }
        if (normalized == "ADMIN" && !enabled) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Administrator calling access is protected")
        }
        if (!roleAccess.portalRoles().any { it.equals(normalized, true) }) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Employee role not found")
        }

        if (enabled) {
            if (!rolePermissions.existsByRoleIgnoreCaseAndPermissionIgnoreCase(normalized, "CALL_CUSTOMER")) {
                rolePermissions.save(RolePermissionEntity(role = normalized, permission = "CALL_CUSTOMER"))
            }
        } else {
            rolePermissions.deleteByRoleIgnoreCaseAndPermissionIgnoreCase(normalized, "CALL_CUSTOMER")
            employees.findAllByRoleInOrderByCreatedAtDesc(listOf(normalized))
                .forEach { voiceCalls.terminateActiveCallForEmployee(requireNotNull(it.id), "CALL_ACCESS_REVOKED") }
        }

        audit.record(
            actor = viewer,
            action = if (enabled) "ROLE_PERMISSION_GRANTED" else "ROLE_PERMISSION_REVOKED",
            subjectType = "ROLE",
            subjectId = normalized,
            summary = if (enabled) "Allowed calling for the $normalized role." else "Removed calling from the $normalized role."
        )

        return VoiceCallRoleAccessResponse(normalized, enabled || normalized == "ADMIN")
    }

    fun userAccess(viewer: EmployeeEntity): List<VoiceCallUserAccessResponse> {
        roleAccess.requirePermission(viewer, "MANAGE_CALL_ACCESS")
        val staffRoles = roleAccess.portalRoles().filterNot { it.equals("CLIENT", true) }
        return employees.findAllByRoleInOrderByCreatedAtDesc(staffRoles).map { toUserResponse(it) }
    }

    fun setUserAccess(viewer: EmployeeEntity, publicId: String, mode: String): VoiceCallUserAccessResponse {
        roleAccess.requirePermission(viewer, "MANAGE_CALL_ACCESS")
        val target = employees.findByPublicId(publicId.trim()).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Employee account not found")
        }
        if (target.role.equals("ADMIN", true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Administrator calling access is protected")
        }

        when (val normalized = mode.trim().uppercase()) {
            "DEFAULT" -> overrides.findByEmployeeIdAndPermissionIgnoreCase(requireNotNull(target.id), "CALL_CUSTOMER")?.let(overrides::delete)
            "ALLOW" -> saveOverride(target, true, viewer)
            "DENY" -> {
                saveOverride(target, false, viewer)
                voiceCalls.terminateActiveCallForEmployee(requireNotNull(target.id), "CALL_ACCESS_REVOKED")
            }
            "INHERIT" -> overrides.findByEmployeeIdAndPermissionIgnoreCase(requireNotNull(target.id), "CALL_CUSTOMER")?.let(overrides::delete)
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid calling access choice")
        }

        audit.record(
            actor = viewer,
            action = "EMPLOYEE_PERMISSION_CHANGED",
            subjectType = "EMPLOYEE",
            subjectId = target.publicId,
            summary = "Changed calling access for ${target.name ?: target.mobile}.",
            metadata = mapOf("permission" to "CALL_CUSTOMER", "mode" to normalized)
        )
        return toUserResponse(target)
    }

    private fun saveOverride(target: EmployeeEntity, allowed: Boolean, viewer: EmployeeEntity) {
        val employeeId = requireNotNull(target.id)
        val now = Instant.now()
        val existing = overrides.findByEmployeeIdAndPermissionIgnoreCase(employeeId, "CALL_CUSTOMER")
        if (existing == null) {
            overrides.save(
                EmployeePermissionOverrideEntity(
                    employeeId = employeeId,
                    permission = "CALL_CUSTOMER",
                    allowed = allowed,
                    changedByEmployeeId = requireNotNull(viewer.id),
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            existing.allowed = allowed
            existing.changedByEmployeeId = requireNotNull(viewer.id)
            existing.updatedAt = now
            overrides.save(existing)
        }
    }

    private fun toUserResponse(employee: EmployeeEntity): VoiceCallUserAccessResponse {
        val override = requireNotNull(employee.id).let {
            overrides.findByEmployeeIdAndPermissionIgnoreCase(it, "CALL_CUSTOMER")
        }
        val inherited = roleAccess.hasRolePermission(employee.role, "CALL_CUSTOMER")
        return VoiceCallUserAccessResponse(
            publicUserId = employee.publicId,
            name = employee.name,
            mobile = employee.mobile,
            role = employee.role.uppercase(),
            mode = when (override?.allowed) {
                true -> "ALLOW"
                false -> "DENY"
                null -> "DEFAULT"
            },
            enabled = override?.allowed ?: inherited
        )
    }
}
