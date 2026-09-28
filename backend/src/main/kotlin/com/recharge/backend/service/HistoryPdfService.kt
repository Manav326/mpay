
package com.recharge.backend.service

import com.recharge.backend.domain.RechargeTransactionEntity
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.domain.WalletTransactionEntity
import com.recharge.backend.repository.RechargeTransactionRepository
import com.recharge.backend.repository.UserRepository
import com.recharge.backend.repository.WalletRepository
import com.recharge.backend.repository.WalletTransactionRepository
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class HistoryPdfService(
    private val users: UserRepository,
    private val wallets: WalletRepository,
    private val recharges: RechargeTransactionRepository,
    private val ledger: WalletTransactionRepository,
    private val access: HistoryPdfAccessService
) {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")

    fun generate(userId: Long, type: String, fromDate: LocalDate?, toDate: LocalDate?, status: String?): ByteArray {
        access.requireApproved(userId)
        val user = users.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        val reportType = type.trim().uppercase()
        require(reportType == "RECHARGE" || reportType == "WALLET") { "type must be RECHARGE or WALLET" }

        val today = LocalDate.now(zone)
        val from = fromDate ?: today
        val to = toDate ?: from
        require(!from.isAfter(to)) { "from must be on or before to" }
        require(!to.isAfter(today)) { "Future history dates are not allowed" }
        require(!to.isAfter(from.plusDays(366))) { "PDF history range cannot exceed 367 days" }
        val fromInstant = from.atStartOfDay(zone).toInstant()
        val toExclusive = to.plusDays(1).atStartOfDay(zone).toInstant()
        val normalizedStatus = status?.trim()?.uppercase()?.takeIf { it.isNotBlank() && it != "ALL" }

        val rows = if (reportType == "RECHARGE") {
            val data = when (normalizedStatus) {
                null -> recharges.findAllByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, fromInstant, toExclusive)
                "PENDING" -> recharges.findAllByUserIdAndStatusInAndCreatedAtBetweenOrderByCreatedAtDesc(userId, listOf("PENDING", "RESERVED"), fromInstant, toExclusive)
                else -> recharges.findAllByUserIdAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(userId, normalizedStatus, fromInstant, toExclusive)
            }
            require(data.size <= 5000) { "Too many records for one PDF. Please narrow the date range." }
            data.map(::rechargeRow)
        } else {
            val data = ledger.findAllByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, fromInstant, toExclusive)
            require(data.size <= 5000) { "Too many records for one PDF. Please narrow the date range." }
            data.map(::walletRow)
        }
        val currentBalance = wallets.findByUserId(userId).orElseThrow().balance
        val reportId = UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
        val generatedAt = Instant.now()
        return render(user, reportType, from, to, normalizedStatus, rows, currentBalance, reportId, generatedAt)
    }

    private fun rechargeRow(tx: RechargeTransactionEntity) = PdfRow(
        tx.createdAt,
        buildString {
            append(tx.operator)
            append(" / ")
            append(maskMobile(tx.mobileNumber))
            append(" / Recharge INR ")
            append(tx.amount.money())
            if (tx.walletDebitAmount.compareTo(tx.amount) != 0) {
                append(" / Wallet debit INR ")
                append(tx.walletDebitAmount.money())
            }
            if (!tx.planDescription.isNullOrBlank()) {
                append(" / ")
                append(tx.planDescription!!.take(28))
            }
        },
        tx.walletDebitAmount.negate(),
        tx.status,
        tx.transactionId
    )

    private fun walletRow(tx: WalletTransactionEntity): PdfRow {
        val label = (tx.referenceType ?: tx.type).replace('_', ' ').uppercase()
        val detail = if (tx.description.isNullOrBlank()) label else label + " / " + tx.description!!.take(44)
        return PdfRow(tx.createdAt, detail, if (tx.type.equals("CREDIT", true)) tx.amount else tx.amount.negate(), tx.status, tx.referenceId ?: tx.externalRef)
    }

    private fun render(
        user: UserEntity,
        type: String,
        from: LocalDate,
        to: LocalDate,
        status: String?,
        rows: List<PdfRow>,
        currentBalance: BigDecimal,
        reportId: String,
        generatedAt: Instant
    ): ByteArray {
        PDDocument().use { document ->
            val navy = Color(20, 33, 54)
            val gold = Color(218, 165, 32)
            val light = Color(245, 247, 250)
            val muted = Color(100, 110, 125)
            val green = Color(27, 126, 77)
            val red = Color(177, 55, 55)
            val black = Color(35, 39, 45)
            val margin = 38f
            val width = PDRectangle.A4.width
            val height = PDRectangle.A4.height
            val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
            var pageNumber = 1
            var page = PDPage(PDRectangle.A4)
            document.addPage(page)
            var stream = PDPageContentStream(document, page)

            fun txt(value: String, x: Float, y: Float, size: Float, font: PDType1Font = regular, color: Color = black) {
                stream.beginText()
                stream.setFont(font, size)
                stream.setNonStrokingColor(color)
                stream.newLineAtOffset(x, y)
                stream.showText(value.safePdf())
                stream.endText()
            }
            fun fill(x: Float, y: Float, w: Float, h: Float, color: Color) {
                stream.setNonStrokingColor(color); stream.addRect(x, y, w, h); stream.fill()
            }
            fun rule(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, width: Float = 1f) {
                stream.setStrokingColor(color); stream.setLineWidth(width); stream.moveTo(x1, y1); stream.lineTo(x2, y2); stream.stroke()
            }
            fun footer() {
                rule(margin, 27f, width - margin, 27f, Color(220, 224, 230), .5f)
                txt("mPay  /  Secure account statement", margin, 16f, 7.5f, regular, muted)
                txt("Page " + pageNumber, width - margin - 34f, 16f, 7.5f, regular, muted)
            }
            fun tableHeader(y: Float): Float {
                val cols = floatArrayOf(87f, 214f, 80f, 65f, 80f)
                val labels = arrayOf("DATE & TIME", "DETAIL", "WALLET IMPACT", "STATUS", "REFERENCE")
                val tableWidth = cols.sum()
                fill(margin, y - 20f, tableWidth, 23f, navy)
                var x = margin
                labels.forEachIndexed { i, label ->
                    txt(label, x + 6f, y - 12f, 6.5f, bold, Color.WHITE); x += cols[i]
                }
                return y - 20f
            }

            fun drawTop() {
                fill(0f, height - 82f, width, 82f, navy)
                txt("mPay", margin, height - 42f, 23f, bold, Color.WHITE)
                txt(if (type == "RECHARGE") "Recharge history" else "Wallet history", margin, height - 62f, 9.5f, regular, Color(220, 228, 240))
                txt("TRANSACTION STATEMENT", width - margin - 118f, height - 39f, 8f, bold, gold)
                txt("ID " + reportId, width - margin - 118f, height - 53f, 6.8f, regular, Color(220, 228, 240))
                txt(generatedAt.atZone(zone).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")), width - margin - 118f, height - 65f, 6.8f, regular, Color(220, 228, 240))
                rule(margin, height - 84f, width - margin, height - 84f, gold, 2.5f)
            }

            drawTop()
            var y = height - 108f
            txt(user.name?.take(50) ?: "mPay customer", margin, y, 13f, bold)
            txt("Account holder", margin, y - 13f, 7.5f, regular, muted)
            txt(maskMobile(user.mobile), width - margin - 82f, y, 10f, bold)
            txt("Mobile", width - margin - 82f, y - 13f, 7.5f, regular, muted)
            txt("Current balance  INR " + currentBalance.setScale(2, RoundingMode.HALF_UP).toPlainString(), width - margin - 190f, y - 29f, 7.5f, regular, muted)

            y -= 43f
            fill(margin, y - 34f, width - margin * 2, 34f, light)
            txt("PERIOD", margin + 10f, y - 13f, 6.8f, bold, muted)
            txt(dateFormatter.format(from) + " - " + dateFormatter.format(to), margin + 10f, y - 27f, 9f, bold)
            if (status != null) {
                txt("STATUS", width / 2f + 10f, y - 13f, 6.8f, bold, muted)
                txt(status, width / 2f + 10f, y - 27f, 9f, bold)
            }
            y -= 52f

            val credits = rows.filter { it.amount.signum() > 0 }.sumOf { it.amount }.setScale(2, RoundingMode.HALF_UP)
            val debits = rows.filter { it.amount.signum() < 0 }.sumOf { it.amount.abs() }.setScale(2, RoundingMode.HALF_UP)
            val boxW = (width - margin * 2 - 14f) / 3f
            val summaries = arrayOf(
                "RECORDS" to rows.size.toString(),
                "CREDITS" to "INR " + credits.money(),
                "DEBITS" to "INR " + debits.money()
            )
            summaries.forEachIndexed { i, item ->
                val x = margin + i * (boxW + 7f)
                fill(x, y - 39f, boxW, 39f, Color(249, 250, 252))
                txt(item.first, x + 9f, y - 13f, 6.7f, bold, muted)
                txt(item.second, x + 9f, y - 29f, 9.5f, bold, if (i == 1) green else if (i == 2) red else black)
            }
            y -= 57f
            val cols = floatArrayOf(87f, 214f, 80f, 65f, 80f)
            val tableWidth = cols.sum()
            y = tableHeader(y)

            rows.forEachIndexed { index, row ->
                val rowH = 31f
                if (y - rowH < 42f) {
                    footer(); stream.close()
                    page = PDPage(PDRectangle.A4); document.addPage(page)
                    stream = PDPageContentStream(document, page); pageNumber++
                    drawTop(); y = height - 108f
                    y = tableHeader(y)
                }
                if (index % 2 == 1) fill(margin, y - rowH + 2f, tableWidth, rowH, Color(250, 251, 253))
                val amountColor = if (row.amount.signum() >= 0) green else red
                val statusColor = when (row.status.uppercase()) {
                    "SUCCESS", "POSTED" -> green
                    "FAILED", "CANCELLED", "REJECTED" -> red
                    else -> black
                }
                val values = arrayOf(
                    row.timestamp.atZone(zone).format(dateTimeFormatter).take(21),
                    row.detail.take(54),
                    (if (row.amount.signum() >= 0) "+" else "-") + "INR " + row.amount.abs().money(),
                    row.status,
                    row.reference.take(25)
                )
                var x = margin
                values.forEachIndexed { i, value ->
                    txt(value, x + 6f, y - 15f, if (i == 1) 7.2f else 7f, if (i == 2 || i == 3) bold else regular,
                        if (i == 2) amountColor else if (i == 3) statusColor else black)
                    x += cols[i]
                }
                rule(margin, y - rowH + 2f, margin + tableWidth, y - rowH + 2f, Color(226, 229, 234), .5f)
                y -= rowH
            }
            footer()
            stream.close()
            val output = ByteArrayOutputStream()
            document.save(output)
            return output.toByteArray()
        }
    }

    private fun maskMobile(mobile: String): String =
        if (mobile.length >= 10) mobile.take(2) + "******" + mobile.takeLast(2) else mobile

    private fun BigDecimal.money(): String = setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun String.safePdf(): String =
        replace("₹", "INR").replace("–", "-").replace("—", "-").replace("•", " / ").replace("→", "->")
            .map { if (it.code <= 127) it else '?' }.joinToString("")

    private data class PdfRow(
        val timestamp: Instant,
        val detail: String,
        val amount: BigDecimal,
        val status: String,
        val reference: String
    )
}
