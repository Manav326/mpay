package com.recharge.backend.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.recharge.backend.api.PortalStaffActivityResponse
import com.recharge.backend.domain.EmployeeActivityEntity
import com.recharge.backend.domain.EmployeeEntity
import com.recharge.backend.repository.EmployeeActivityRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class EmployeeAuditService(
    private val activities: EmployeeActivityRepository,
    private val objectMapper: ObjectMapper
) {
    fun record(
        actor: EmployeeEntity,
        action: String,
        subjectType: String? = null,
        subjectId: String? = null,
        summary: String,
        metadata: Map<String, Any?> = emptyMap()
    ) {
        save(
            employeeId = requireNotNull(actor.id),
            action = action,
            subjectType = subjectType,
            subjectId = subjectId,
            summary = summary,
            metadata = metadata
        )
    }

    fun list(employee: EmployeeEntity): List<PortalStaffActivityResponse> =
        activities.findTop100ByEmployeeIdOrderByOccurredAtDescIdDesc(requireNotNull(employee.id)).map {
            PortalStaffActivityResponse(
                action = it.action,
                subjectType = it.subjectType,
                subjectId = it.subjectId,
                summary = it.summary,
                occurredAt = it.occurredAt
            )
        }

    fun recordById(
        employeeId: Long,
        action: String,
        subjectType: String? = null,
        subjectId: String? = null,
        summary: String,
        metadata: Map<String, Any?> = emptyMap()
    ) {
        save(
            employeeId = employeeId,
            action = action,
            subjectType = subjectType,
            subjectId = subjectId,
            summary = summary,
            metadata = metadata
        )
    }

    private fun save(
        employeeId: Long,
        action: String,
        subjectType: String?,
        subjectId: String?,
        summary: String,
        metadata: Map<String, Any?>
    ) {
        activities.save(
            EmployeeActivityEntity(
                employeeId = employeeId,
                action = action.take(80),
                subjectType = subjectType?.take(50),
                subjectId = subjectId?.take(120),
                summary = summary.take(500),
                metadataJson = toMetadataNode(metadata),
                occurredAt = Instant.now()
            )
        )
    }

    private fun toMetadataNode(metadata: Map<String, Any?>): JsonNode? =
        metadata
            .filterValues { it != null }
            .takeIf { it.isNotEmpty() }
            ?.let { values ->
                runCatching { objectMapper.valueToTree<JsonNode>(values) }.getOrNull()
            }
}
