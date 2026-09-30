package com.recharge.backend.service

import com.recharge.backend.api.CurrentUserResponse
import com.recharge.backend.api.ProfileUpdateRequest
import com.recharge.backend.domain.EmployeeEntity
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.repository.EmployeeRepository
import com.recharge.backend.repository.UserRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class ProfileService(
    private val users: UserRepository,
    private val employees: EmployeeRepository,
    private val imageStorage: ProfileImageStorage,
    private val commissionRateService: CommissionRateService,
    private val employeeAudit: EmployeeAuditService
) {
    fun getProfile(userId: Long): CurrentUserResponse = toResponse(requireUser(userId))

    fun getEmployeeProfile(employeeId: Long): CurrentUserResponse = toResponse(requireEmployee(employeeId))

    @Transactional
    fun updateProfile(userId: Long, request: ProfileUpdateRequest): CurrentUserResponse {
        val user = requireUser(userId)
        val email = request.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (email != null && !EMAIL_REGEX.matches(email)) throw IllegalArgumentException("Email must be valid")
        if (email != null && users.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
            throw IllegalArgumentException("This email address is already in use")
        }

        user.name = request.name?.trim()?.takeIf { it.isNotBlank() }
        user.email = email
        user.profileUpdatedAt = java.time.Instant.now()
        users.save(user)
        return toResponse(user)
    }

    @Transactional
    fun updateEmployeeProfile(employeeId: Long, request: ProfileUpdateRequest): CurrentUserResponse {
        val employee = requireEmployee(employeeId)
        val email = request.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (email != null && !EMAIL_REGEX.matches(email)) throw IllegalArgumentException("Email must be valid")
        val existing = employees.findByEmailIgnoreCase(email ?: "")
        if (email != null && existing.isPresent && existing.get().id != employeeId) {
            throw IllegalArgumentException("This email address is already in use")
        }

        employee.name = request.name?.trim()?.takeIf { it.isNotBlank() }
        employee.email = email
        employee.profileUpdatedAt = java.time.Instant.now()
        employee.updatedAt = employee.profileUpdatedAt!!
        employees.save(employee)
        employeeAudit.recordById(
            employeeId = employeeId,
            action = "PROFILE_UPDATED",
            subjectType = "EMPLOYEE",
            subjectId = employee.publicId,
            summary = "Updated employee profile details."
        )
        return toResponse(employee)
    }

    @Transactional
    fun uploadImage(userId: Long, file: MultipartFile): CurrentUserResponse {
        val user = requireUser(userId)
        val newKey = imageStorage.save(user.publicId, file)
        val oldKey = user.profileImageKey
        user.profileImageKey = newKey
        user.profileImageContentType = file.contentType?.lowercase()
        val now = java.time.Instant.now()
        user.profileImageUpdatedAt = now
        user.profileUpdatedAt = now
        users.save(user)
        if (oldKey != null) imageStorage.delete(oldKey)
        return toResponse(user)
    }

    @Transactional
    fun uploadEmployeeImage(employeeId: Long, file: MultipartFile): CurrentUserResponse {
        val employee = requireEmployee(employeeId)
        val newKey = imageStorage.save(employee.publicId, file)
        val oldKey = employee.profileImageKey
        employee.profileImageKey = newKey
        employee.profileImageContentType = file.contentType?.lowercase()
        val now = java.time.Instant.now()
        employee.profileImageUpdatedAt = now
        employee.profileUpdatedAt = now
        employee.updatedAt = now
        employees.save(employee)
        if (oldKey != null) imageStorage.delete(oldKey)
        employeeAudit.recordById(
            employeeId = employeeId,
            action = "PROFILE_IMAGE_CHANGED",
            subjectType = "EMPLOYEE",
            subjectId = employee.publicId,
            summary = "Changed employee profile photo."
        )
        return toResponse(employee)
    }

    @Transactional
    fun deleteImage(userId: Long): CurrentUserResponse {
        val user = requireUser(userId)
        val oldKey = user.profileImageKey
        user.profileImageKey = null
        user.profileImageContentType = null
        val now = java.time.Instant.now()
        user.profileImageUpdatedAt = now
        user.profileUpdatedAt = now
        users.save(user)
        imageStorage.delete(oldKey)
        return toResponse(user)
    }

    @Transactional
    fun deleteEmployeeImage(employeeId: Long): CurrentUserResponse {
        val employee = requireEmployee(employeeId)
        val oldKey = employee.profileImageKey
        employee.profileImageKey = null
        employee.profileImageContentType = null
        val now = java.time.Instant.now()
        employee.profileImageUpdatedAt = now
        employee.profileUpdatedAt = now
        employee.updatedAt = now
        employees.save(employee)
        imageStorage.delete(oldKey)
        employeeAudit.recordById(
            employeeId = employeeId,
            action = "PROFILE_IMAGE_REMOVED",
            subjectType = "EMPLOYEE",
            subjectId = employee.publicId,
            summary = "Removed employee profile photo."
        )
        return toResponse(employee)
    }

    fun image(userId: Long, variant: ImageVariant): ProfileImageStorage.StoredImage {
        val user = requireUser(userId)
        val key = user.profileImageKey ?: throw IllegalArgumentException("Profile image not found")
        return imageStorage.load(key, variant) ?: throw IllegalArgumentException("Profile image not found")
    }

    fun employeeImage(employeeId: Long, variant: ImageVariant): ProfileImageStorage.StoredImage {
        val employee = requireEmployee(employeeId)
        val key = employee.profileImageKey ?: throw IllegalArgumentException("Profile image not found")
        return imageStorage.load(key, variant) ?: throw IllegalArgumentException("Profile image not found")
    }

    private fun requireUser(userId: Long) = users.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
    private fun requireEmployee(employeeId: Long) = employees.findById(employeeId).orElseThrow { IllegalArgumentException("Employee not found") }

    companion object {
        private val EMAIL_REGEX = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
    }

    private fun toResponse(user: UserEntity): CurrentUserResponse {
        val id = requireNotNull(user.id)
        val version = user.profileImageUpdatedAt?.toEpochMilli()
        val url = user.profileImageKey?.let { "/api/v1/profile/image" + (version?.let { v -> "?v=$v" } ?: "") }
        return CurrentUserResponse(
            userId = id,
            publicUserId = user.publicId,
            mobile = user.mobile,
            name = user.name,
            email = user.email,
            profileImageUrl = url,
            profileImageVersion = version,
            role = user.role,
            commissionRate = commissionRateService.rateForRole(user.role),
            createdAt = user.createdAt,
            profileUpdatedAt = user.profileUpdatedAt
        )
    }

    private fun toResponse(employee: EmployeeEntity): CurrentUserResponse {
        val id = requireNotNull(employee.id)
        val version = employee.profileImageUpdatedAt?.toEpochMilli()
        val url = employee.profileImageKey?.let { "/api/v1/profile/image" + (version?.let { v -> "?v=$v" } ?: "") }
        return CurrentUserResponse(
            userId = id,
            publicUserId = employee.publicId,
            mobile = employee.mobile,
            name = employee.name,
            email = employee.email,
            profileImageUrl = url,
            profileImageVersion = version,
            role = employee.role,
            commissionRate = commissionRateService.rateForRole(employee.role),
            createdAt = employee.createdAt,
            profileUpdatedAt = employee.profileUpdatedAt
        )
    }
}
