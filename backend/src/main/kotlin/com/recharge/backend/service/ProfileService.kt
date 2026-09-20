package com.recharge.backend.service

import com.recharge.backend.api.CurrentUserResponse
import com.recharge.backend.api.ProfileUpdateRequest
import com.recharge.backend.repository.UserRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal

@Service
class ProfileService(
    private val users: UserRepository,
    private val imageStorage: ProfileImageStorage,
    private val commissionRateService: CommissionRateService
) {
    fun getProfile(userId: Long): CurrentUserResponse = toResponse(requireUser(userId))

    @Transactional
    fun updateProfile(userId: Long, request: ProfileUpdateRequest): CurrentUserResponse {
        val user = requireUser(userId)
        val email = request.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (email != null && !EMAIL_REGEX.matches(email)) {
            throw IllegalArgumentException("Email must be valid")
        }
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

    fun image(userId: Long): ProfileImageStorage.StoredImage {
        val user = requireUser(userId)
        val key = user.profileImageKey ?: throw IllegalArgumentException("Profile image not found")
        return imageStorage.load(key) ?: throw IllegalArgumentException("Profile image not found")
    }

    private fun requireUser(userId: Long) = users.findById(userId).orElseThrow { IllegalArgumentException("User not found") }

    companion object {
        private val EMAIL_REGEX = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
    }

    private fun toResponse(user: com.recharge.backend.domain.UserEntity): CurrentUserResponse {
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
}
