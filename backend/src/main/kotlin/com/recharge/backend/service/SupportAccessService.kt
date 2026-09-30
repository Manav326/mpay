package com.recharge.backend.service

import com.recharge.backend.api.SupportAccessResponse
import com.recharge.backend.api.SupportPermissionStateResponse
import com.recharge.backend.api.SupportRoleAccessResponse
import com.recharge.backend.api.SupportUserAccessResponse
import com.recharge.backend.domain.EmployeeEntity
import com.recharge.backend.domain.EmployeePermissionOverrideEntity
import com.recharge.backend.domain.RolePermissionEntity
import com.recharge.backend.repository.EmployeePermissionOverrideRepository
import com.recharge.backend.repository.EmployeeRepository
import com.recharge.backend.repository.RolePermissionRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class SupportAccessService(
    private val roleAccess: RoleAccessService,
    private val rolePermissions: RolePermissionRepository,
    private val overrides: EmployeePermissionOverrideRepository,
    private val employees: EmployeeRepository,
    private val voiceCalls: VoiceCallService,
    private val audit: EmployeeAuditService
) {
    companion object {
        const val MANAGE_SUPPORT_ACCESS = "MANAGE_SUPPORT_ACCESS"
        const val SUPPORT_VIEW = "SUPPORT_VIEW"
        const val SUPPORT_MANAGE = "SUPPORT_MANAGE"
        const val CALL_CUSTOMER = "CALL_CUSTOMER"
        const val MANAGE_CALL_ACCESS = "MANAGE_CALL_ACCESS"
        const val MANAGE_SUPPORT_AI = "MANAGE_SUPPORT_AI"
        const val SUPPORT_VIEW_CUSTOMER_CONTEXT = "SUPPORT_VIEW_CUSTOMER_CONTEXT"

        private data class Definition(
            val permission: String,
            val label: String,
            val description: String,
            val group: String
        )

        private val definitions = listOf(
            Definition(SUPPORT_VIEW, "Handle customer conversations", "Open Customer Care and read customer conversations and support cases.", "Customer Care"),
            Definition(SUPPORT_MANAGE, "Reply and manage cases", "Reply to customers, add internal notes, and resolve or reopen cases.", "Customer Care"),
            Definition(SUPPORT_VIEW_CUSTOMER_CONTEXT, "View customer money details", "See wallet balance and recent recharge or money activity while helping a customer.", "Customer information"),
            Definition(CALL_CUSTOMER, "Call customers", "Start a two-way support call after the customer accepts it.", "Voice"),
            Definition(MANAGE_CALL_ACCESS, "Manage callback settings", "Change callback access and voice-related support settings.", "Voice"),
            Definition(MANAGE_SUPPORT_AI, "Manage Customer Care AI", "Turn customer-facing Customer Care AI on or off and manage its settings.", "AI")
        )

        private val assignablePermissions = definitions.map { it.permission }.toSet()
    }

    fun access(viewer: EmployeeEntity): SupportAccessResponse {
        requireManager(viewer)

        val staffRoles = roleAccess.portalRoles()
            .map { it.uppercase() }
            .filterNot { it.equals("CLIENT", true) }
            .distinct()
            .sorted()

        val roles = staffRoles.map { role ->
            SupportRoleAccessResponse(
                role = role,
                protected = role.equals("ADMIN", true),
                permissions = definitions.map { rolePermissionState(role, it) }
            )
        }

        val staffUsers = employees.findAllByRoleInOrderByCreatedAtDesc(staffRoles)
            .filterNot { it.role.equals("ADMIN", true) }
            .sortedWith(compareBy<EmployeeEntity> { it.role.uppercase() }.thenBy { it.name ?: "" })
            .map(::userAccess)

        return SupportAccessResponse(
            permissions = definitions.map {
                SupportPermissionStateResponse(
                    permission = it.permission,
                    label = it.label,
                    description = it.description,
                    group = it.group,
                    enabled = false,
                    mode = null,
                    inherited = false,
                    editable = true,
                    lockedReason = null
                )
            },
            roles = roles,
            users = staffUsers
        )
    }

    @Transactional
    fun setRolePermission(viewer: EmployeeEntity, role: String, permission: String, enabled: Boolean): SupportRoleAccessResponse {
        requireManager(viewer)
        val normalizedRole = role.trim().uppercase()
        val definition = definition(permission)

        if (normalizedRole.equals("CLIENT", true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer accounts cannot receive staff permissions")
        }
        if (normalizedRole.equals("ADMIN", true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Administrator access is protected")
        }
        if (!roleAccess.portalRoles().any { it.equals(normalizedRole, true) }) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Employee role not found")
        }

        if (enabled) {
            ensureRolePermission(normalizedRole, definition.permission)
            if (definition.permission == SUPPORT_MANAGE) {
                ensureRolePermission(normalizedRole, SUPPORT_VIEW)
            }
        } else {
            rolePermissions.deleteByRoleIgnoreCaseAndPermissionIgnoreCase(normalizedRole, definition.permission)
            if (definition.permission == SUPPORT_VIEW) {
                rolePermissions.deleteByRoleIgnoreCaseAndPermissionIgnoreCase(normalizedRole, SUPPORT_MANAGE)
            }
            if (definition.permission == CALL_CUSTOMER) {
                employees.findAllByRoleInOrderByCreatedAtDesc(listOf(normalizedRole))
                    .forEach { employee ->
                        voiceCalls.terminateActiveCallForEmployee(requireNotNull(employee.id), "CALL_ACCESS_REVOKED")
                    }
            }
        }

        audit.record(
            actor = viewer,
            action = if (enabled) "ROLE_PERMISSION_GRANTED" else "ROLE_PERMISSION_REVOKED",
            subjectType = "ROLE",
            subjectId = normalizedRole,
            summary = if (enabled) "Allowed ${definition.label.lowercase()} for the $normalizedRole role." else "Removed ${definition.label.lowercase()} from the $normalizedRole role."
        )
        return roleAccessFor(normalizedRole)
    }

    @Transactional
    fun setUserPermission(viewer: EmployeeEntity, publicEmployeeId: String, permission: String, mode: String): SupportUserAccessResponse {
        requireManager(viewer)
        val target = employees.findByPublicId(publicEmployeeId.trim()).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Employee account not found")
        }
        if (target.role.equals("ADMIN", true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Administrator access is protected")
        }

        val definition = definition(permission)
        val normalizedMode = mode.trim().uppercase()
        if (normalizedMode !in setOf("DEFAULT", "ALLOW", "DENY")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid access choice")
        }

        if (definition.permission == SUPPORT_VIEW && normalizedMode == "DEFAULT" && roleAccess.hasPermission(target, SUPPORT_MANAGE)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Remove case-management access before returning this employee to the normal role setting")
        }

        when (normalizedMode) {
            "DEFAULT" -> deleteOverride(target, definition.permission)
            "ALLOW" -> {
                saveOverride(target, definition.permission, true, viewer)
                if (definition.permission == SUPPORT_MANAGE) saveOverride(target, SUPPORT_VIEW, true, viewer)
            }
            "DENY" -> {
                saveOverride(target, definition.permission, false, viewer)
                if (definition.permission == SUPPORT_VIEW) saveOverride(target, SUPPORT_MANAGE, false, viewer)
                if (definition.permission == CALL_CUSTOMER) {
                    voiceCalls.terminateActiveCallForEmployee(requireNotNull(target.id), "CALL_ACCESS_REVOKED")
                }
            }
        }

        audit.record(
            actor = viewer,
            action = "EMPLOYEE_PERMISSION_CHANGED",
            subjectType = "EMPLOYEE",
            subjectId = target.publicId,
            summary = when (normalizedMode) {
                "ALLOW" -> "Allowed ${definition.label.lowercase()} for ${target.name ?: target.mobile}."
                "DENY" -> "Removed ${definition.label.lowercase()} for ${target.name ?: target.mobile}."
                else -> "Returned ${definition.label.lowercase()} to the employee role setting for ${target.name ?: target.mobile}."
            },
            metadata = mapOf("permission" to definition.permission, "mode" to normalizedMode)
        )
        return userAccess(target)
    }

    private fun requireManager(viewer: EmployeeEntity) {
        roleAccess.requirePermission(viewer, MANAGE_SUPPORT_ACCESS)
    }

    private fun definition(permission: String): Definition {
        val normalized = permission.trim().uppercase()
        if (normalized !in assignablePermissions) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported access option")
        }
        return definitions.first { it.permission == normalized }
    }

    private fun rolePermissionState(role: String, definition: Definition): SupportPermissionStateResponse =
        SupportPermissionStateResponse(
            permission = definition.permission,
            label = definition.label,
            description = definition.description,
            group = definition.group,
            enabled = roleAccess.hasRolePermission(role, definition.permission),
            mode = null,
            inherited = false,
            editable = !role.equals("ADMIN", true),
            lockedReason = if (role.equals("ADMIN", true)) "Administrator access is protected" else null
        )

    private fun roleAccessFor(role: String): SupportRoleAccessResponse =
        SupportRoleAccessResponse(
            role = role.uppercase(),
            protected = role.equals("ADMIN", true),
            permissions = definitions.map { rolePermissionState(role, it) }
        )

    private fun userAccess(employee: EmployeeEntity): SupportUserAccessResponse =
        SupportUserAccessResponse(
            publicUserId = employee.publicId,
            name = employee.name,
            mobile = employee.mobile,
            role = employee.role.uppercase(),
            protected = employee.role.equals("ADMIN", true),
            permissions = definitions.map { definition ->
                val id = requireNotNull(employee.id)
                val override = overrides.findByEmployeeIdAndPermissionIgnoreCase(id, definition.permission)
                SupportPermissionStateResponse(
                    permission = definition.permission,
                    label = definition.label,
                    description = definition.description,
                    group = definition.group,
                    enabled = override?.allowed ?: roleAccess.hasRolePermission(employee.role, definition.permission),
                    mode = when (override?.allowed) {
                        true -> "ALLOW"
                        false -> "DENY"
                        null -> "DEFAULT"
                    },
                    inherited = override == null,
                    editable = !employee.role.equals("ADMIN", true),
                    lockedReason = if (employee.role.equals("ADMIN", true)) "Administrator access is protected" else null
                )
            }
        )

    private fun ensureRolePermission(role: String, permission: String) {
        if (!rolePermissions.existsByRoleIgnoreCaseAndPermissionIgnoreCase(role, permission)) {
            rolePermissions.save(RolePermissionEntity(role = role, permission = permission))
        }
    }

    private fun saveOverride(target: EmployeeEntity, permission: String, allowed: Boolean, viewer: EmployeeEntity) {
        val employeeId = requireNotNull(target.id)
        val now = Instant.now()
        val existing = overrides.findByEmployeeIdAndPermissionIgnoreCase(employeeId, permission)
        if (existing == null) {
            overrides.save(
                EmployeePermissionOverrideEntity(
                    employeeId = employeeId,
                    permission = permission,
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

    private fun deleteOverride(target: EmployeeEntity, permission: String) {
        overrides.findByEmployeeIdAndPermissionIgnoreCase(requireNotNull(target.id), permission)?.let(overrides::delete)
    }
}
