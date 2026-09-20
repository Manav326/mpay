package com.recharge.backend.service

import com.recharge.backend.domain.UserEntity
import com.recharge.backend.repository.RoleHierarchyRepository
import com.recharge.backend.repository.RolePermissionRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service

@Service
class RoleAccessService(
    private val permissions: RolePermissionRepository,
    private val hierarchy: RoleHierarchyRepository
) {
    fun permissionsFor(role: String): Set<String> = permissions
        .findAllByRoleIgnoreCaseOrderByPermissionAsc(role)
        .map { it.permission.uppercase() }
        .toSet()

    fun hasPermission(role: String, permission: String): Boolean =
        permissions.existsByRoleIgnoreCaseAndPermissionIgnoreCase(role, permission)

    fun requirePermission(viewer: UserEntity, permission: String) {
        if (!hasPermission(viewer.role, permission)) {
            throw AccessDeniedException("Permission required: $permission")
        }
    }

    fun visibleRolesFor(role: String): Set<String> = hierarchy
        .findAllByViewerRoleIgnoreCaseOrderByTargetRoleAsc(role)
        .map { it.targetRole.uppercase() }
        .toSet()

    fun canView(viewer: UserEntity, target: UserEntity): Boolean =
        visibleRolesFor(viewer.role).contains(target.role.uppercase())

    fun requireCanView(viewer: UserEntity, target: UserEntity) {
        requirePermission(viewer, "VIEW_USER_DETAIL")
        if (!canView(viewer, target)) {
            throw AccessDeniedException("You cannot view this user")
        }
    }

    fun portalRoles(): List<String> = permissions
        .findAllByPermissionIgnoreCaseOrderByRoleAsc("PORTAL_LOGIN")
        .map { it.role.uppercase() }
        .distinct()
}
