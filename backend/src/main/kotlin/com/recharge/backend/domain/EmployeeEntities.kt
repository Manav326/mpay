package com.recharge.backend.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "employees",
    indexes = [
        Index(name = "idx_employees_role_active", columnList = "role,active"),
        Index(name = "idx_employees_created", columnList = "created_at DESC")
    ]
)
class EmployeeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    var publicId: String = UUID.randomUUID().toString(),
    @Column(nullable = false, unique = true, length = 15)
    var mobile: String = "",
    @Column(nullable = true, length = 120)
    var name: String? = null,
    @Column(nullable = true, unique = true, length = 254)
    var email: String? = null,
    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String = "",
    @Column(nullable = false, length = 50)
    var role: String = "",
    @Column(nullable = false)
    var active: Boolean = true,
    @Column(name = "profile_image_key", length = 255)
    var profileImageKey: String? = null,
    @Column(name = "profile_image_content_type", length = 100)
    var profileImageContentType: String? = null,
    @Column(name = "profile_image_updated_at")
    var profileImageUpdatedAt: Instant? = null,
    @Column(name = "profile_updated_at")
    var profileUpdatedAt: Instant? = null,
    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(
    name = "employee_permission_overrides",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_employee_permission_override",
            columnNames = ["employee_id", "permission"]
        )
    ],
    indexes = [
        Index(name = "idx_employee_permission_overrides_employee", columnList = "employee_id")
    ]
)
class EmployeePermissionOverrideEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "employee_id", nullable = false)
    var employeeId: Long = 0,
    @Column(nullable = false, length = 80)
    var permission: String = "",
    @Column(nullable = false)
    var allowed: Boolean = true,
    @Column(name = "changed_by_employee_id", nullable = false)
    var changedByEmployeeId: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(
    name = "employee_activity",
    indexes = [
        Index(name = "idx_employee_activity_employee_time", columnList = "employee_id,occurred_at DESC"),
        Index(name = "idx_employee_activity_subject_time", columnList = "subject_type,subject_id,occurred_at DESC")
    ]
)
class EmployeeActivityEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "employee_id", nullable = false)
    var employeeId: Long = 0,
    @Column(name = "action", nullable = false, length = 80)
    var action: String = "",
    @Column(name = "subject_type", length = 50)
    var subjectType: String? = null,
    @Column(name = "subject_id", length = 120)
    var subjectId: String? = null,
    @Column(nullable = false, length = 500)
    var summary: String = "",
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    var metadataJson: String? = null,
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now()
)
