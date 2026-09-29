package com.recharge.backend.service

import com.recharge.backend.api.VoiceCallRoleAccessResponse
import com.recharge.backend.api.VoiceCallUserAccessResponse
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.domain.UserPermissionOverrideEntity
import com.recharge.backend.repository.RolePermissionRepository
import com.recharge.backend.repository.UserPermissionOverrideRepository
import com.recharge.backend.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class VoiceCallAccessService(
    private val roleAccess: RoleAccessService,
    private val rolePermissions: RolePermissionRepository,
    private val overrides: UserPermissionOverrideRepository,
    private val users: UserRepository,
    private val voiceCalls: VoiceCallService
) {
    fun roleAccess(viewer: UserEntity): List<VoiceCallRoleAccessResponse> {
        requireAdmin(viewer)
        return roleAccess.portalRoles()
            .filterNot { it.equals("CLIENT", true) }
            .map { role ->
                VoiceCallRoleAccessResponse(role.uppercase(), roleAccess.hasRolePermission(role, "CALL_CUSTOMER"))
            }
            .sortedBy { it.role }
    }

    fun setRoleAccess(viewer: UserEntity, role: String, enabled: Boolean): VoiceCallRoleAccessResponse {
        requireAdmin(viewer)
        val normalized = role.trim().uppercase()
        if (normalized.isBlank() || normalized.equals("CLIENT", true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Voice calling can only be assigned to portal staff roles")
        }
        if (normalized == "ADMIN" && !enabled) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The ADMIN role always retains voice-calling access")
        }
        if (!roleAccess.portalRoles().any { it.equals(normalized, true) }) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Portal role not found")
        }

        if (enabled) {
            if (!rolePermissions.existsByRoleIgnoreCaseAndPermissionIgnoreCase(normalized, "CALL_CUSTOMER")) {
                rolePermissions.save(com.recharge.backend.domain.RolePermissionEntity(role = normalized, permission = "CALL_CUSTOMER"))
            }
        } else {
            rolePermissions.deleteByRoleIgnoreCaseAndPermissionIgnoreCase(normalized, "CALL_CUSTOMER")
            users.findAllByRoleInOrderByCreatedAtDesc(listOf(normalized))
                .forEach { voiceCalls.terminateActiveCallForUser(requireNotNull(it.id), "CALL_ACCESS_REVOKED") }
        }

        return VoiceCallRoleAccessResponse(normalized, enabled || normalized == "ADMIN")
    }

    fun userAccess(viewer: UserEntity): List<VoiceCallUserAccessResponse> {
        requireAdmin(viewer)
        val staffRoles = roleAccess.portalRoles().filterNot { it.equals("CLIENT", true) }
        return users.findAllByRoleInOrderByCreatedAtDesc(staffRoles)
            .map { toUserResponse(it) }
    }

    fun setUserAccess(viewer: UserEntity, publicId: String, mode: String): VoiceCallUserAccessResponse {
        requireAdmin(viewer)
        val target = users.findByPublicId(publicId.trim()).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Staff account not found")
        }
        if (target.role.equals("ADMIN", true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The ADMIN role always retains voice-calling access")
        }
        if (target.role.equals("CLIENT", true) || !roleAccess.portalRoles().any { it.equals(target.role, true) }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Voice calling access can only be assigned to portal staff")
        }

        val normalized = mode.trim().uppercase()
        when (normalized) {
            "DEFAULT", "INHERIT" -> overrides.findByUserIdAndPermissionIgnoreCase(requireNotNull(target.id), "CALL_CUSTOMER")
                ?.let { overrides.delete(it) }
            "ALLOW" -> saveOverride(target, true, viewer)
            "DENY" -> {
                saveOverride(target, false, viewer)
                voiceCalls.terminateActiveCallForUser(requireNotNull(target.id), "CALL_ACCESS_REVOKED")
            }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Mode must be DEFAULT, ALLOW, or DENY")
        }

        return toUserResponse(target)
    }

    private fun saveOverride(target: UserEntity, allowed: Boolean, viewer: UserEntity) {
        val userId = requireNotNull(target.id)
        val existing = overrides.findByUserIdAndPermissionIgnoreCase(userId, "CALL_CUSTOMER")
        val now = Instant.now()
        if (existing == null) {
            overrides.save(
                UserPermissionOverrideEntity(
                    userId = userId,
                    permission = "CALL_CUSTOMER",
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

    private fun toUserResponse(user: UserEntity): VoiceCallUserAccessResponse {
        val override = requireNotNull(user.id).let { overrides.findByUserIdAndPermissionIgnoreCase(it, "CALL_CUSTOMER") }
        val inherited = roleAccess.hasRolePermission(user.role, "CALL_CUSTOMER")
        return VoiceCallUserAccessResponse(
            publicUserId = user.publicId,
            name = user.name,
            mobile = user.mobile,
            role = user.role.uppercase(),
            mode = when (override?.allowed) {
                true -> "ALLOW"
                false -> "DENY"
                null -> "DEFAULT"
            },
            enabled = override?.allowed ?: inherited
        )
    }

    private fun requireAdmin(viewer: UserEntity) {
        roleAccess.requirePermission(viewer, "MANAGE_CALL_ACCESS")
    }
}
