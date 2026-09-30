package com.recharge.backend.service

import com.fasterxml.jackson.databind.ObjectMapper
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
        val employeeId = requireNotNull(actor.id)
        val json = metadata.filterValues { it != null }.takeIf { it.isNotEmpty() }?.let {
            runCatching { objectMapper.writeValueAsString(it) }.getOrNull()
        }
        activities.save(
            EmployeeActivityEntity(
                employeeId = employeeId,
                action = action.take(80),
                subjectType = subjectType?.take(50),
                subjectId = subjectId?.take(120),
                summary = summary.take(500),
                metadataJson = json,
                occurredAt = Instant.now()
            )
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
        val json = metadata.filterValues { it != null }.takeIf { it.isNotEmpty() }?.let {
            runCatching { objectMapper.writeValueAsString(it) }.getOrNull()
        }
        activities.save(
            EmployeeActivityEntity(
                employeeId = employeeId,
                action = action.take(80),
                subjectType = subjectType?.take(50),
                subjectId = subjectId?.take(120),
                summary = summary.take(500),
                metadataJson = json,
                occurredAt = Instant.now()
            )
        )
    }
}
