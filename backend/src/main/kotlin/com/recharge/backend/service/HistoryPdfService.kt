
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
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
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
    private val brandLogoBytes: ByteArray by lazy {
        requireNotNull(HistoryPdfService::class.java.getResourceAsStream("/branding/mpay-logo.png")) {
            "mPay brand logo resource not found"
        }.use { it.readBytes() }
    }

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
                append(tx.planDescription!!.trim())
            }
        },
        tx.walletDebitAmount.negate(),
        tx.status,
        tx.transactionId
    )

    private fun walletRow(tx: WalletTransactionEntity): PdfRow {
        val label = (tx.referenceType ?: tx.type).replace('_', ' ').uppercase()
        val detail = if (tx.description.isNullOrBlank()) label else label + " / " + tx.description!!.trim()
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
            // The PDF deliberately uses the same core brand tokens as the Android/web clients:
            // Vendor Navy for the premium header, Primary/PrimaryDark for the mPay amber brand,
            // and the warm paper/surface neutrals used throughout the product.
            val navy = Color(16, 42, 67)          // #102A43
            val gold = Color(245, 158, 11)        // #F59E0B
            val goldDark = Color(217, 119, 6)     // #D97706
            val paper = Color(255, 251, 243)      // #FFFBF3
            val surfaceWarm = Color(255, 243, 217) // #FFF3D9
            val neutral = Color(248, 250, 252)    // #F8FAFC
            val border = Color(238, 231, 221)     // #EEE7DD
            val muted = Color(115, 115, 115)      // #737373
            val green = Color(22, 163, 74)        // #16A34A
            val red = Color(220, 38, 38)          // #DC2626
            val black = Color(23, 23, 23)         // #171717
            val headerText = Color(238, 243, 248)
            val margin = 38f
            val width = PDRectangle.A4.width
            val height = PDRectangle.A4.height
            val contentWidth = width - margin * 2f
            val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
            val brandLogo = PDImageXObject.createFromByteArray(document, brandLogoBytes, "mpay-logo")
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

            fun textWidth(value: String, font: PDType1Font, size: Float): Float =
                font.getStringWidth(value.safePdf()) / 1000f * size

            fun txtRight(value: String, rightX: Float, y: Float, size: Float, font: PDType1Font = regular, color: Color = black) {
                val safe = value.safePdf()
                txt(safe, rightX - textWidth(safe, font, size), y, size, font, color)
            }

            fun fitSingleLine(value: String, maxWidth: Float, size: Float, font: PDType1Font = regular): String {
                val safe = value.safePdf().trim()
                if (safe.isEmpty() || textWidth(safe, font, size) <= maxWidth) return safe
                val suffix = "..."
                var candidate = safe
                while (candidate.isNotEmpty() && textWidth(candidate + suffix, font, size) > maxWidth) {
                    candidate = candidate.dropLast(1)
                }
                return (candidate.trimEnd() + suffix).takeIf { it.length <= safe.length + suffix.length } ?: suffix
            }

            fun wrapDetail(value: String, maxWidth: Float, size: Float): List<String> {
                val safe = value.safePdf().trim()
                if (safe.isEmpty()) return listOf("")
                val words = safe.split(Regex("\\s+"))
                val lines = mutableListOf<String>()
                var current = ""

                for (word in words) {
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (textWidth(candidate, regular, size) <= maxWidth) {
                        current = candidate
                        continue
                    }

                    if (current.isNotEmpty()) {
                        lines += current
                    }

                    if (lines.size == 2) {
                        val remaining = if (current.isEmpty()) word else "$current $word"
                        lines[1] = fitSingleLine(remaining, maxWidth, size)
                        current = ""
                        break
                    }

                    current = word
                    if (textWidth(current, regular, size) > maxWidth) {
                        current = fitSingleLine(current, maxWidth, size)
                    }
                }

                if (current.isNotEmpty() && lines.size < 2) lines += current
                if (lines.isEmpty()) lines += fitSingleLine(safe, maxWidth, size)
                if (lines.size > 2) {
                    lines[1] = fitSingleLine(lines.drop(1).joinToString(" "), maxWidth, size)
                    return lines.take(2)
                }
                return lines
            }

            fun fill(x: Float, y: Float, w: Float, h: Float, color: Color) {
                stream.setNonStrokingColor(color)
                stream.addRect(x, y, w, h)
                stream.fill()
            }

            fun rule(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, width: Float = 1f) {
                stream.setStrokingColor(color)
                stream.setLineWidth(width)
                stream.moveTo(x1, y1)
                stream.lineTo(x2, y2)
                stream.stroke()
            }

            fun footer() {
                rule(margin, 27f, width - margin, 27f, border, .6f)
                txt("mPay  /  Secure account statement", margin, 16f, 7.4f, regular, muted)
                txtRight("Report " + reportId, width - margin - 42f, 16f, 7.4f, regular, muted)
                txtRight("Page " + pageNumber, width - margin, 16f, 7.4f, bold, muted)
            }

            fun tableHeader(y: Float): Float {
                val cols = floatArrayOf(87f, 214f, 80f, 65f, 80f)
                val labels = arrayOf("DATE & TIME", "DETAIL", "WALLET IMPACT", "STATUS", "REFERENCE")
                val tableWidth = cols.sum()
                fill(margin, y - 20f, tableWidth, 23f, navy)
                var x = margin
                labels.forEachIndexed { i, label ->
                    txt(label, x + 7f, y - 12f, 6.5f, bold, Color.WHITE)
                    x += cols[i]
                }
                rule(margin, y - 20f, margin + tableWidth, y - 20f, gold, 1.4f)
                return y - 20f
            }

            fun drawTop() {
                fill(0f, height - 94f, width, 94f, navy)

                // Canonical logo mark + wordmark + tagline, matching the shared web/Android lockup.
                stream.drawImage(brandLogo, margin, height - 66f, 34f, 34f)
                txt("mPay", margin + 45f, height - 39f, 21f, bold, gold)
                txt("SECURE  /  SIMPLE  /  SMART", margin + 45f, height - 53f, 6.5f, bold, headerText)
                txt(if (type == "RECHARGE") "RECHARGE HISTORY" else "WALLET HISTORY", margin + 45f, height - 68f, 7.8f, bold, Color.WHITE)

                txtRight("TRANSACTION STATEMENT", width - margin, height - 31f, 8f, bold, gold)
                txtRight("REPORT ID  " + reportId, width - margin, height - 46f, 6.6f, bold, headerText)
                txtRight(generatedAt.atZone(zone).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")), width - margin, height - 58f, 6.6f, regular, headerText)
                txtRight("Generated by mPay", width - margin, height - 70f, 6.2f, regular, Color(187, 201, 214))

                // Deliberate brand-color keyline separating the branded masthead from the statement body.
                fill(margin, height - 98f, contentWidth, 2.5f, gold)
            }

            drawTop()
            var y = height - 121f

            // Account identity: three equal columns, designed for a fast visual scan.
            fill(margin, y - 43f, contentWidth, 43f, paper)
            rule(margin, y - 43f, width - margin, y - 43f, border, .7f)
            val colW = contentWidth / 3f
            val identityXs = floatArrayOf(margin, margin + colW, margin + colW * 2f)

            txt("ACCOUNT HOLDER", identityXs[0] + 10f, y - 12f, 6.5f, bold, muted)
            txt(fitSingleLine(user.name?.take(50) ?: "mPay customer", colW - 20f, 10.5f, bold), identityXs[0] + 10f, y - 28f, 10.5f, bold)

            txt("MOBILE", identityXs[1] + 10f, y - 12f, 6.5f, bold, muted)
            txt(maskMobile(user.mobile), identityXs[1] + 10f, y - 28f, 10.5f, bold)

            txt("CURRENT BALANCE", identityXs[2] + 10f, y - 12f, 6.5f, bold, muted)
            txtRight("INR " + currentBalance.setScale(2, RoundingMode.HALF_UP).toPlainString(), identityXs[2] + colW - 10f, y - 28f, 10.5f, bold, black)

            // Scope/filter card: the user can understand exactly what this report represents before
            // reaching the data table.
            y -= 53f
            fill(margin, y - 38f, contentWidth, 38f, surfaceWarm)
            rule(margin, y - 38f, width - margin, y - 38f, border, .7f)

            txt("STATEMENT PERIOD", margin + 10f, y - 12f, 6.5f, bold, goldDark)
            txt(dateFormatter.format(from) + "  -  " + dateFormatter.format(to), margin + 10f, y - 27f, 9f, bold, black)

            val filterX = margin + contentWidth * 0.62f
            txt("STATUS FILTER", filterX, y - 12f, 6.5f, bold, goldDark)
            txt(status ?: "ALL STATUSES", filterX, y - 27f, 9f, bold, black)

            y -= 50f

            // Summary cards remain three cards, but use the same warm/neutral surfaces as the
            // product so they read like one coherent information block rather than generic boxes.
            val credits = rows.filter { it.amount.signum() > 0 }.sumOf { it.amount }.setScale(2, RoundingMode.HALF_UP)
            val debits = rows.filter { it.amount.signum() < 0 }.sumOf { it.amount.abs() }.setScale(2, RoundingMode.HALF_UP)
            val boxGap = 7f
            val boxW = (contentWidth - boxGap * 2f) / 3f
            val summaries = arrayOf(
                "RECORDS" to rows.size.toString(),
                "CREDITS" to "INR " + credits.money(),
                "DEBITS" to "INR " + debits.money()
            )
            summaries.forEachIndexed { i, item ->
                val x = margin + i * (boxW + boxGap)
                fill(x, y - 39f, boxW, 39f, if (i == 0) surfaceWarm else neutral)
                rule(x, y - 39f, x + boxW, y - 39f, border, .6f)
                txt(item.first, x + 9f, y - 13f, 6.5f, bold, muted)
                txt(item.second, x + 9f, y - 29f, 9.5f, bold, if (i == 1) green else if (i == 2) red else black)
            }

            y -= 57f
            val cols = floatArrayOf(87f, 214f, 80f, 65f, 80f)
            val tableWidth = cols.sum()
            y = tableHeader(y)

            rows.forEachIndexed { index, row ->
                val detailWidth = cols[1] - 12f
                val detailSize = 7.2f
                val detailLines = wrapDetail(row.detail, detailWidth, detailSize)
                val rowH = if (detailLines.size > 1) 43f else 31f

                if (y - rowH < 42f) {
                    footer()
                    stream.close()
                    page = PDPage(PDRectangle.A4)
                    document.addPage(page)
                    stream = PDPageContentStream(document, page)
                    pageNumber++
                    drawTop()
                    y = height - 121f
                    y = tableHeader(y)
                }

                if (index % 2 == 1) {
                    fill(margin, y - rowH + 2f, tableWidth, rowH, Color(251, 252, 253))
                }

                val amountColor = if (row.amount.signum() >= 0) green else red
                val statusColor = when (row.status.uppercase()) {
                    "SUCCESS", "POSTED" -> green
                    "FAILED", "CANCELLED", "REJECTED" -> red
                    else -> goldDark
                }
                val textSize = 7f
                val dateValue = row.timestamp.atZone(zone).format(dateTimeFormatter)
                val amountValue = (if (row.amount.signum() >= 0) "+" else "-") + "INR " + row.amount.abs().money()
                val referenceValue = if (row.reference.isBlank()) "-" else row.reference

                var x = margin
                txt(fitSingleLine(dateValue, cols[0] - 12f, textSize), x + 6f, y - if (rowH > 31f) 18f else 15f, textSize, regular, black)
                x += cols[0]

                if (detailLines.size == 1) {
                    txt(detailLines[0], x + 6f, y - 15f, detailSize, regular, black)
                } else {
                    txt(detailLines[0], x + 6f, y - 12f, detailSize, regular, black)
                    txt(detailLines[1], x + 6f, y - 23f, detailSize, regular, black)
                }
                x += cols[1]

                txt(
                    fitSingleLine(amountValue, cols[2] - 12f, textSize, bold),
                    x + 6f,
                    y - if (rowH > 31f) 18f else 15f,
                    textSize,
                    bold,
                    amountColor
                )
                x += cols[2]

                txt(
                    fitSingleLine(row.status, cols[3] - 12f, textSize, bold),
                    x + 6f,
                    y - if (rowH > 31f) 18f else 15f,
                    textSize,
                    bold,
                    statusColor
                )
                x += cols[3]

                txt(
                    fitSingleLine(referenceValue, cols[4] - 12f, textSize),
                    x + 6f,
                    y - if (rowH > 31f) 18f else 15f,
                    textSize,
                    regular,
                    black
                )

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
