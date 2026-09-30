package com.recharge.backend.service

import com.recharge.backend.api.SupportAccessResponse
import com.recharge.backend.api.SupportPermissionStateResponse
import com.recharge.backend.api.SupportRoleAccessResponse
import com.recharge.backend.api.SupportUserAccessResponse
import com.recharge.backend.domain.RolePermissionEntity
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.domain.UserPermissionOverrideEntity
import com.recharge.backend.repository.RolePermissionRepository
import com.recharge.backend.repository.UserPermissionOverrideRepository
import com.recharge.backend.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class SupportAccessService(
    private val roleAccess: RoleAccessService,
    private val rolePermissions: RolePermissionRepository,
    private val overrides: UserPermissionOverrideRepository,
    private val users: UserRepository,
    private val voiceCalls: VoiceCallService
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
            Definition(
                SUPPORT_VIEW,
                "View Customer Care",
                "Open the Customer Care workspace and read customer conversations, cases and support history.",
                "Customer Care"
            ),
            Definition(
                SUPPORT_MANAGE,
                "Reply & manage cases",
                "Send customer replies, add notes, and resolve or reopen support cases.",
                "Customer Care"
            ),
            Definition(
                SUPPORT_VIEW_CUSTOMER_CONTEXT,
                "Customer financial context",
                "View wallet balance and recent money/recharge context in the support side panel.",
                "Customer context"
            ),
            Definition(
                CALL_CUSTOMER,
                "Voice calling",
                "Start two-way support calls to customers who explicitly accept the call.",
                "Voice"
            ),
            Definition(
                MANAGE_CALL_ACCESS,
                "Callback access",
                "Change which customers may request a support callback and manage voice-access controls.",
                "Voice"
            ),
            Definition(
                MANAGE_SUPPORT_AI,
                "AI controls",
                "Manage the customer-facing Customer Care AI availability and settings.",
                "AI"
            )
        )

        private val assignablePermissions = definitions.map { it.permission }.toSet()
    }

    fun access(viewer: UserEntity): SupportAccessResponse {
        requireManager(viewer)

        val staffRoles = roleAccess.portalRoles()
            .map { it.uppercase() }
            .distinct()
            .sorted()

        val roles = staffRoles.map { role ->
            SupportRoleAccessResponse(
                role = role,
                protected = role.equals("ADMIN", true),
                permissions = definitions.map { definition ->
                    rolePermissionState(role, definition)
                }
            )
        }

        val staffUsers = users.findAllByRoleInOrderByCreatedAtDesc(staffRoles)
            .sortedWith(compareBy<UserEntity> { it.role.uppercase() }.thenBy { it.name ?: "" })
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
    fun setRolePermission(viewer: UserEntity, role: String, permission: String, enabled: Boolean): SupportRoleAccessResponse {
        requireManager(viewer)
        val normalizedRole = normalizeRole(role)
        val definition = definition(permission)
        if (normalizedRole.equals("CLIENT", true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer accounts cannot receive portal support permissions")
        }
        if (normalizedRole.equals("ADMIN", true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The ADMIN role always retains protected support access")
        }
        if (!roleAccess.portalRoles().any { it.equals(normalizedRole, true) }) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Portal role not found")
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
                users.findAllByRoleInOrderByCreatedAtDesc(listOf(normalizedRole))
                    .forEach { user -> voiceCalls.terminateActiveCallForUser(requireNotNull(user.id), "CALL_ACCESS_REVOKED") }
            }
        }

        return roleAccessFor(normalizedRole)
    }

    @Transactional
    fun setUserPermission(
        viewer: UserEntity,
        publicUserId: String,
        permission: String,
        mode: String
    ): SupportUserAccessResponse {
        requireManager(viewer)
        val target = users.findByPublicId(publicUserId.trim()).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Staff account not found")
        }
        if (target.role.equals("ADMIN", true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The ADMIN account keeps protected support access")
        }
        if (target.role.equals("CLIENT", true) || !roleAccess.portalRoles().any { it.equals(target.role, true) }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Support permissions can only be assigned to portal staff")
        }

        val definition = definition(permission)
        val normalizedMode = mode.trim().uppercase()
        if (normalizedMode !in setOf("DEFAULT", "ALLOW", "DENY")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Mode must be DEFAULT, ALLOW, or DENY")
        }

        if (definition.permission == SUPPORT_VIEW && normalizedMode == "DEFAULT" && roleAccess.hasPermission(target, SUPPORT_MANAGE)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Revoke case management first before returning Customer Care view access to the role default"
            )
        }

        when (normalizedMode) {
            "DEFAULT" -> deleteOverride(target, definition.permission)
            "ALLOW" -> {
                saveOverride(target, definition.permission, true, viewer)
                if (definition.permission == SUPPORT_MANAGE) {
                    saveOverride(target, SUPPORT_VIEW, true, viewer)
                }
            }
            "DENY" -> {
                saveOverride(target, definition.permission, false, viewer)
                if (definition.permission == SUPPORT_VIEW) {
                    saveOverride(target, SUPPORT_MANAGE, false, viewer)
                }
                if (definition.permission == CALL_CUSTOMER) {
                    voiceCalls.terminateActiveCallForUser(requireNotNull(target.id), "CALL_ACCESS_REVOKED")
                }
            }
        }

        return userAccess(target)
    }

    private fun requireManager(viewer: UserEntity) {
        roleAccess.requirePermission(viewer, MANAGE_SUPPORT_ACCESS)
    }

    private fun definition(permission: String): Definition {
        val normalized = permission.trim().uppercase()
        if (normalized !in assignablePermissions) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported Customer Care permission")
        }
        return definitions.first { it.permission == normalized }
    }

    private fun normalizeRole(role: String): String = role.trim().uppercase()

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
            lockedReason = if (role.equals("ADMIN", true)) "Protected administrator access" else null
        )

    private fun roleAccessFor(role: String): SupportRoleAccessResponse {
        val normalized = normalizeRole(role)
        return SupportRoleAccessResponse(
            role = normalized,
            protected = normalized.equals("ADMIN", true),
            permissions = definitions.map { rolePermissionState(normalized, it) }
        )
    }

    private fun userAccess(user: UserEntity): SupportUserAccessResponse =
        SupportUserAccessResponse(
            publicUserId = user.publicId,
            name = user.name,
            mobile = user.mobile,
            role = user.role.uppercase(),
            protected = user.role.equals("ADMIN", true),
            permissions = definitions.map { definition ->
                val userId = requireNotNull(user.id)
                val override = overrides.findByUserIdAndPermissionIgnoreCase(userId, definition.permission)
                SupportPermissionStateResponse(
                    permission = definition.permission,
                    label = definition.label,
                    description = definition.description,
                    group = definition.group,
                    enabled = override?.allowed ?: roleAccess.hasRolePermission(user.role, definition.permission),
                    mode = when (override?.allowed) {
                        true -> "ALLOW"
                        false -> "DENY"
                        null -> "DEFAULT"
                    },
                    inherited = override == null,
                    editable = !user.role.equals("ADMIN", true),
                    lockedReason = if (user.role.equals("ADMIN", true)) "Protected administrator access" else null
                )
            }
        )

    private fun ensureRolePermission(role: String, permission: String) {
        if (!rolePermissions.existsByRoleIgnoreCaseAndPermissionIgnoreCase(role, permission)) {
            rolePermissions.save(RolePermissionEntity(role = role, permission = permission))
        }
    }

    private fun saveOverride(target: UserEntity, permission: String, allowed: Boolean, viewer: UserEntity) {
        val userId = requireNotNull(target.id)
        val now = Instant.now()
        val existing = overrides.findByUserIdAndPermissionIgnoreCase(userId, permission)
        if (existing == null) {
            overrides.save(
                UserPermissionOverrideEntity(
                    userId = userId,
                    permission = permission,
                    allowed = allowed,
                    grantedByUserId = requireNotNull(viewer.id),
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            existing.allowed = allowed
            existing.grantedByUserId = requireNotNull(viewer.id)
            existing.updatedAt = now
            overrides.save(existing)
        }
    }

    private fun deleteOverride(target: UserEntity, permission: String) {
        overrides.findByUserIdAndPermissionIgnoreCase(requireNotNull(target.id), permission)?.let(overrides::delete)
    }
}
