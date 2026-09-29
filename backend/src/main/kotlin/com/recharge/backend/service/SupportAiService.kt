package com.recharge.backend.service

import com.fasterxml.jackson.databind.JsonNode
import com.recharge.backend.config.SupportAiProperties
import com.recharge.backend.repository.SupportConversationRepository
import com.recharge.backend.repository.SupportMessageRepository
import com.recharge.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.client.RestClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class SupportAiService(
    private val properties: SupportAiProperties,
    private val settings: SupportAiSettingsService,
    private val restClient: RestClient,
    private val knowledgeBase: SupportAiKnowledgeBase,
    private val customerContext: SupportAiCustomerContextService,
    private val conversations: SupportConversationRepository,
    private val messages: SupportMessageRepository,
    private val users: UserRepository,
    private val support: SupportService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val locks = ConcurrentHashMap<Long, ReentrantLock>()

    @Async("supportAiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleCustomerMessage(event: SupportCustomerMessageCreatedEvent) {
        if (!settings.isEnabled() || !properties.providerConfigured) return

        locks.computeIfAbsent(event.conversationId) { ReentrantLock() }.withLock {
            try {
                generateReply(event)
            } catch (error: Exception) {
                logger.warn("mPay support AI failed for conversation " + event.conversationId, error)
            }
        }
    }

    private fun generateReply(event: SupportCustomerMessageCreatedEvent) {
        val conversation = conversations.findById(event.conversationId).orElse(null) ?: return
        if (conversation.customerUserId != event.customerUserId || conversation.status != "OPEN") return

        val latest = messages.findAllByConversationIdOrderByCreatedAtAsc(event.conversationId).lastOrNull() ?: return
        if (latest.messageId != event.messageId || latest.senderType != "CUSTOMER") return

        val customer = users.findById(event.customerUserId).orElse(null) ?: return
        if (!customer.active || customer.deletedAt != null || !customer.role.equals("CLIENT", true)) return

        val history = messages.findAllByConversationIdOrderByCreatedAtAsc(event.conversationId).takeLast(properties.maxHistoryMessages.coerceIn(4, 24))
        val transcript = history.joinToString("\n") {
            val speaker = when (it.senderType) {
                "CUSTOMER" -> "CUSTOMER"
                "AI" -> "mPay AI"
                "STAFF" -> "mPay Support"
                else -> it.senderType
            }
            speaker + ": " + it.message
        }

        val knowledge = knowledgeBase.findRelevant(event.message, properties.maxKnowledgeCharacters.coerceAtLeast(2000))
        val customerFacts = customerContext.buildContext(event.customerUserId, event.message)

        val instruction = buildString {
            appendLine("You are mPay Customer Care AI.")
            appendLine("You are an AI support assistant inside the authenticated mPay customer chat.")
            appendLine("Answer only from the supplied mPay knowledge and authenticated customer facts.")
            appendLine("Never invent transaction states, fees, refunds, timings, policies, or actions.")
            appendLine("Never claim that money was credited, debited, refunded, recharged, withdrawn, booked, or cancelled unless the supplied backend fact explicitly supports it.")
            appendLine("Never reveal internal database fields, secrets, provider credentials, UPI IDs, account numbers, or internal identifiers.")
            appendLine("Do not perform or imply that you performed a financial or account-changing action.")
            appendLine("For disputes, uncertain cases, security/account-access issues, or an explicit request for a human, tell the customer that mPay Customer Care will take over rather than guessing.")
            appendLine("Keep replies concise, practical, and friendly.")
            appendLine("Reply in the customer's language when reasonably clear; Hindi/Hinglish and English are supported.")
            appendLine()
            appendLine("AUTHORITATIVE mPay KNOWLEDGE:")
            appendLine(knowledge.ifBlank { "No matching mPay knowledge was found. Do not invent an answer." })
            customerFacts?.let {
                appendLine()
                appendLine(it)
            }
            appendLine()
            appendLine("RECENT SUPPORT CHAT:")
            appendLine(transcript)
            appendLine()
            appendLine("Answer the latest CUSTOMER message only.")
        }

        val body = linkedMapOf<String, Any>(
            "model" to properties.model,
            "instructions" to instruction,
            "input" to event.message,
            "max_output_tokens" to 600
        )

        if (properties.vectorStoreId.isNotBlank()) {
            body["tools"] = listOf(
                mapOf(
                    "type" to "file_search",
                    "vector_store_ids" to listOf(properties.vectorStoreId),
                    "max_num_results" to 4
                )
            )
        }

        val response = restClient.post()
            .uri("/v1/responses")
            .header("Authorization", "Bearer " + properties.apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode::class.java) ?: return

        val reply = extractOutputText(response)?.trim()?.takeIf { it.isNotBlank() } ?: return

        if (!settings.isEnabled() || !properties.providerConfigured) return
        val currentConversation = conversations.findById(event.conversationId).orElse(null) ?: return
        if (currentConversation.customerUserId != event.customerUserId || currentConversation.status != "OPEN") return
        val latestAfterResponse = messages.findAllByConversationIdOrderByCreatedAtAsc(event.conversationId).lastOrNull() ?: return
        if (latestAfterResponse.messageId != event.messageId || latestAfterResponse.senderType != "CUSTOMER") return

        support.appendAutomatedSupportMessage(
            conversationId = event.conversationId,
            caseId = event.caseId,
            customerUserId = event.customerUserId,
            messageText = reply
        )
    }

    private fun extractOutputText(response: JsonNode): String? {
        response.path("output_text").takeIf { it.isTextual }?.asText()?.let { return it }

        val output = response.path("output")
        if (!output.isArray) return null
        for (item in output) {
            if (item.path("type").asText() != "message") continue
            for (content in item.path("content")) {
                if (content.path("type").asText() == "output_text") {
                    val text = content.path("text").asText("")
                    if (text.isNotBlank()) return text
                }
            }
        }
        return null
    }
}
