package com.recharge.backend.service

data class SupportCustomerMessageCreatedEvent(
    val messageId: String,
    val conversationId: Long,
    val caseId: Long?,
    val customerUserId: Long,
    val message: String
)
