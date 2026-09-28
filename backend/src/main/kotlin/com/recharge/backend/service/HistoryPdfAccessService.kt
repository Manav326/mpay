package com.recharge.backend.service

import com.recharge.backend.api.HistoryPdfAccessResponse
import com.recharge.backend.api.HistoryPdfPendingAccessResponse
import com.recharge.backend.domain.HistoryPdfAccessRequestEntity
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.repository.HistoryPdfAccessRequestRepository
import com.recharge.backend.repository.UserRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class HistoryPdfAccessService(
    private val requests: HistoryPdfAccessRequestRepository,
    private val users: UserRepository,
    private val roleAccess: RoleAccessService
) {
    companion object { const val FEATURE_KEY = "HISTORY_PDF_EXPORT" }

    fun status(userId: Long): HistoryPdfAccessResponse =
        requests.findTopByUserIdAndFeatureKeyOrderByRequestedAtDesc(userId, FEATURE_KEY)
            ?.let(::toResponse)
            ?: HistoryPdfAccessResponse("NOT_REQUESTED", null, null, null, null, null)

    fun adminStatus(viewer: UserEntity, targetPublicId: String): HistoryPdfAccessResponse {
        roleAccess.requirePermission(viewer, "MANAGE_HISTORY_PDF_ACCESS")
        val target = users.findByPublicId(targetPublicId).orElseThrow { IllegalArgumentException("User not found") }
        return status(target.id!!)
    }

    fun pending(viewer: UserEntity): List<HistoryPdfPendingAccessResponse> {
        roleAccess.requirePermission(viewer, "MANAGE_HISTORY_PDF_ACCESS")
        return requests.findTop50ByFeatureKeyAndStatusOrderByRequestedAtAsc(FEATURE_KEY, "PENDING")
            .mapNotNull { request ->
                val user = users.findById(request.userId).orElse(null) ?: return@mapNotNull null
                HistoryPdfPendingAccessResponse(
                    requestId = request.id ?: return@mapNotNull null,
                    publicUserId = user.publicId,
                    customerName = user.name,
                    mobile = user.mobile,
                    requestReason = request.requestReason,
                    requestedAt = request.requestedAt
                )
            }
    }

    fun requireApproved(userId: Long) {
        val latest = requests.findTopByUserIdAndFeatureKeyOrderByRequestedAtDesc(userId, FEATURE_KEY)
        if (latest?.status != "APPROVED") {
            throw AccessDeniedException("PDF history access has not been approved for this account.")
        }
    }

    @Transactional
    fun request(userId: Long, reason: String): HistoryPdfAccessResponse {
        val normalized = reason.trim()
        require(normalized.length in 20..1000) {
            "Please provide a satisfactory reason between 20 and 1000 characters."
        }
        val latest = requests.findTopByUserIdAndFeatureKeyOrderByRequestedAtDesc(userId, FEATURE_KEY)
        when (latest?.status) {
            "APPROVED", "PENDING" -> return toResponse(latest)
        }
        return toResponse(
            requests.save(
                HistoryPdfAccessRequestEntity(
                    userId = userId,
                    featureKey = FEATURE_KEY,
                    status = "PENDING",
                    requestReason = normalized,
                    requestedAt = Instant.now()
                )
            )
        )
    }

    @Transactional
    fun decide(
        viewer: UserEntity,
        targetPublicId: String,
        requestId: Long,
        action: String,
        reviewNote: String?
    ): HistoryPdfAccessResponse {
        roleAccess.requirePermission(viewer, "MANAGE_HISTORY_PDF_ACCESS")
        val target = users.findByPublicId(targetPublicId).orElseThrow { IllegalArgumentException("User not found") }
        val request = requests.findById(requestId).orElseThrow { IllegalArgumentException("Access request not found") }
        require(request.userId == target.id && request.featureKey == FEATURE_KEY) { "Invalid access request" }
        val latest = requests.findTopByUserIdAndFeatureKeyOrderByRequestedAtDesc(target.id!!, FEATURE_KEY)
        require(latest?.id == request.id) { "Only the latest access request can be decided" }

        val normalizedAction = action.trim().uppercase()
        require(normalizedAction in setOf("APPROVE", "REJECT", "REVOKE")) { "Unsupported access decision" }
        when (normalizedAction) {
            "APPROVE" -> {
                require(request.status == "PENDING") { "Only pending requests can be approved" }
                request.status = "APPROVED"
            }
            "REJECT" -> {
                require(request.status == "PENDING") { "Only pending requests can be rejected" }
                require(!reviewNote.isNullOrBlank()) { "A rejection note is required." }
                request.status = "REJECTED"
            }
            "REVOKE" -> {
                require(request.status == "APPROVED") { "Only approved access can be revoked" }
                request.status = "REVOKED"
            }
        }
        request.reviewNote = reviewNote?.trim()?.takeIf { it.isNotBlank() }?.take(1000)
        request.reviewedBy = viewer.id
        request.reviewedAt = Instant.now()
        return toResponse(requests.save(request))
    }

    private fun toResponse(entity: HistoryPdfAccessRequestEntity) = HistoryPdfAccessResponse(
        status = entity.status,
        requestId = entity.id,
        requestReason = entity.requestReason,
        reviewNote = entity.reviewNote,
        requestedAt = entity.requestedAt,
        reviewedAt = entity.reviewedAt
    )
}
