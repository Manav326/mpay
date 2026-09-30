package com.recharge.backend.service

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class SupportAiKnowledgeBase {
    private data class Section(val document: String, val text: String)

    private val documents = listOf(
        "support/knowledge/01-product-overview.md",
        "support/knowledge/02-wallet-and-payments.md",
        "support/knowledge/03-recharge.md",
        "support/knowledge/04-rental.md",
        "support/knowledge/05-customer-support.md",
        "support/knowledge/06-ai-safety.md"
    ).flatMap(::loadSections)

    fun findRelevant(query: String, maxCharacters: Int): String {
        val terms = query
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .split(" ")
            .filter { it.length >= 2 }
            .distinct()

        val ranked = documents
            .map { section ->
                val haystack = section.text.lowercase(Locale.ROOT)
                section to terms.count { term -> haystack.contains(term) }
            }
            .sortedWith(
                compareByDescending<Pair<Section, Int>> { it.second }
                    .thenBy { it.first.document }
            )

        val selected = ranked.filter { it.second > 0 }.take(4).ifEmpty { ranked.take(2) }

        return buildString {
            for ((section, _) in selected) {
                val block = "SOURCE: " + section.document + "\n" + section.text.trim() + "\n\n"
                if (length + block.length > maxCharacters) break
                append(block)
            }
        }.trim()
    }

    private fun loadSections(path: String): List<Section> {
        val content = ClassPathResource(path).inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return content
            .split(Regex("(?m)(?=^#{1,3}\\s)"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { Section(path.removePrefix("support/knowledge/"), it.take(3500)) }
    }
}
