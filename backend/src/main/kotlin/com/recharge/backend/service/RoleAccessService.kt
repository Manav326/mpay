package com.recharge.backend.service

import com.recharge.backend.domain.EmployeeEntity
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.repository.EmployeePermissionOverrideRepository
import com.recharge.backend.repository.RoleHierarchyRepository
import com.recharge.backend.repository.RolePermissionRepository
import com.recharge.backend.repository.UserPermissionOverrideRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service

@Service
class RoleAccessService(
    private val permissions: RolePermissionRepository,
    private val hierarchy: RoleHierarchyRepository,
    private val userOverrides: UserPermissionOverrideRepository,
    private val employeeOverrides: EmployeePermissionOverrideRepository
) {
    fun permissionsFor(role: String): Set<String> = permissions
        .findAllByRoleIgnoreCaseOrderByPermissionAsc(role)
        .map { it.permission.uppercase() }
        .toSet()

    fun permissionsFor(user: UserEntity): Set<String> {
        val userId = requireNotNull(user.id) { "User ID is required" }
        val resolved = permissionsFor(user.role).toMutableSet()
        userOverrides.findAllByUserId(userId).forEach { override ->
            if (override.allowed) resolved.add(override.permission.uppercase())
            else resolved.remove(override.permission.uppercase())
        }
        return resolved
    }

    fun permissionsFor(employee: EmployeeEntity): Set<String> {
        val employeeId = requireNotNull(employee.id) { "Employee ID is required" }
        val resolved = permissionsFor(employee.role).toMutableSet()
        employeeOverrides.findAllByEmployeeId(employeeId).forEach { override ->
            if (override.allowed) resolved.add(override.permission.uppercase())
            else resolved.remove(override.permission.uppercase())
        }
        return resolved
    }

    fun hasRolePermission(role: String, permission: String): Boolean =
        permissions.existsByRoleIgnoreCaseAndPermissionIgnoreCase(role, permission)

    fun hasPermission(role: String, permission: String): Boolean =
        hasRolePermission(role, permission)

    fun hasPermission(user: UserEntity, permission: String): Boolean {
        val userId = requireNotNull(user.id) { "User ID is required" }
        val override = userOverrides.findByUserIdAndPermissionIgnoreCase(userId, permission)
        return override?.allowed ?: hasRolePermission(user.role, permission)
    }

    fun hasPermission(employee: EmployeeEntity, permission: String): Boolean {
        val employeeId = requireNotNull(employee.id) { "Employee ID is required" }
        val override = employeeOverrides.findByEmployeeIdAndPermissionIgnoreCase(employeeId, permission)
        return override?.allowed ?: hasRolePermission(employee.role, permission)
    }

    fun requirePermission(viewer: UserEntity, permission: String) {
        if (!hasPermission(viewer, permission)) {
            throw AccessDeniedException("Permission required: $permission")
        }
    }

    fun requirePermission(viewer: EmployeeEntity, permission: String) {
        if (!hasPermission(viewer, permission)) {
            throw AccessDeniedException("Permission required: $permission")
        }
    }

    fun visibleRolesFor(role: String): Set<String> = hierarchy
        .findAllByViewerRoleIgnoreCaseOrderByTargetRoleAsc(role)
        .map { it.targetRole.uppercase() }
        .toSet()

    fun canView(viewer: UserEntity, target: UserEntity): Boolean =
        visibleRolesFor(viewer.role).contains(target.role.uppercase())

    fun canView(viewer: EmployeeEntity, target: UserEntity): Boolean =
        visibleRolesFor(viewer.role).contains(target.role.uppercase())

    fun requireCanView(viewer: UserEntity, target: UserEntity) {
        requirePermission(viewer, "VIEW_USER_DETAIL")
        if (!canView(viewer, target)) {
            throw AccessDeniedException("You cannot view this user")
        }
    }

    fun requireCanView(viewer: EmployeeEntity, target: UserEntity) {
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
