package net.blueshell.api.declaration.domain

import net.blueshell.api.declaration.persistence.Declaration
import net.blueshell.api.declaration.persistence.DeclarationExpense
import net.blueshell.api.declaration.persistence.DeclarationState
import net.blueshell.api.declaration.persistence.DeclarationStep
import net.blueshell.api.file.api.PictureBitmaps
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.pdf.HtmlPdf
import net.blueshell.api.user.api.BankAccountLookup
import net.blueshell.api.user.api.MaskedIban
import net.blueshell.api.user.api.UserService
import org.apache.pdfbox.Loader
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Declarations as one PDF, named for what it holds. */
class DeclarationPdf(
    val name: String,
    val bytes: ByteArray,
)

/** One row of the form's table, as it reads on paper. */
data class ExpenseRow(
    val date: String,
    val description: String,
    val receipts: String?,
    val bookedTo: String,
    val amount: String,
)

/**
 * Declarations as one PDF for the bookkeeping: each in the layout of the association's paper
 * declaration form, then each of its receipts on pages of its own, built each time it is asked
 * for and kept nowhere. Only the preview of the bank account is in it (api ADR-041).
 */
@Service
class DeclarationPdfs(
    private val reviews: DeclarationReviews,
    private val receipts: Receipts,
    private val pictures: PictureBitmaps,
    private val users: UserService,
    private val bankAccounts: BankAccountLookup,
    private val templates: TemplateEngine,
) {
    @Transactional(readOnly = true)
    fun pdf(ids: List<Long>): DeclarationPdf {
        val asked = ids.distinct()
        if (asked.isEmpty() || asked.size > MOST) throw PdfSelectionSize(MOST)
        val declarations = asked.map(reviews::one)
        val people = declarations.flatMap { one -> one.steps.mapNotNull { it.byUserId } + one.userId }
        val names = users.findAllByIds(people.distinct()).associate { it.id to it.fullName }
        val bytes =
            PDDocument().use { out ->
                val pages = ReceiptPages(out)
                declarations.forEach { declaration ->
                    Loader.loadPDF(form(declaration, names)).use { PDFMergerUtility().appendDocument(out, it) }
                    receiptsOf(declaration).forEach(pages::add)
                }
                ByteArrayOutputStream().also(out::save).toByteArray()
            }
        val name = if (declarations.size == 1) "declaration-${declarations.single().id}.pdf" else "declarations.pdf"
        return DeclarationPdf(name, bytes)
    }

    // TWIN: `DeclarationSheet.vue` in the frontend words the form the same way.
    private fun form(
        declaration: Declaration,
        names: Map<Long?, String>,
    ): ByteArray {
        fun by(step: DeclarationStep) = step.byUserId?.let(names::get) ?: "a deleted account"
        // The board sees only what was submitted, so a submit is always on record; steps before the last
        // one belong to an earlier round.
        val submitted = declaration.steps.indexOfLast { it.state == DeclarationState.SUBMITTED }
        val since = declaration.steps.drop(submitted + 1)
        val paid = since.lastOrNull { it.state == DeclarationState.PAID }
        // A paid declaration names the account it went to; any other the account on file now.
        val account = paid?.let { MaskedIban.of(it.paidTo) } ?: bankAccounts.preview(declaration.userId)
        val fields =
            mapOf(
                "title" to "Declaration ${declaration.id}",
                "submitter" to names[declaration.userId].orEmpty(),
                "iban" to (account?.toString() ?: "Not added"),
                "expenses" to declaration.expenses.map(::rowOf),
                "total" to euros(declaration.expenses.sumOf { it.amountCents ?: 0 }),
                "wholeReceipts" to wholeReceipts(declaration.receiptFileIds.size),
                "statement" to declaration.steps[submitted].let { "Submitted on ${day(it.at)} by ${by(it)}. " + STATEMENT },
                "board" to boardLine(since, paid, account, ::by),
            )
        return HtmlPdf.render(templates.process(TEMPLATE, Context(Locale.ENGLISH, fields)))
    }

    private fun rowOf(expense: DeclarationExpense) =
        ExpenseRow(
            date = expense.paidOn?.toString().orEmpty(),
            description = expense.description,
            receipts = receiptCount(expense.receiptFileIds.size),
            bookedTo = expense.bookedTo?.name.orEmpty(),
            amount = euros(expense.amountCents ?: 0),
        )

    private fun boardLine(
        since: List<DeclarationStep>,
        paid: DeclarationStep?,
        account: MaskedIban?,
        by: (DeclarationStep) -> String,
    ): String {
        val rejected = since.lastOrNull { it.state == DeclarationState.REJECTED }
        if (rejected != null) return "Rejected on ${day(rejected.at)} by ${by(rejected)}: ${rejected.reason}"
        val approved = since.lastOrNull { it.state == DeclarationState.APPROVED } ?: return "Not approved yet."
        val paidLine = paid?.let { " Paid on ${day(it.at)}" + (account?.let { to -> " to $to" }.orEmpty()) + "." }
        return "Approved on ${day(approved.at)} by ${by(approved)}." + paidLine.orEmpty()
    }

    /** Each receipt with what it is for: the expenses' in their order, then the declaration's own. */
    private fun receiptsOf(declaration: Declaration): List<LabelledReceipt> {
        val files = receipts.describe(declaration.allReceiptFileIds)
        val ofExpenses =
            declaration.expenses.flatMapIndexed { index, expense ->
                expense.receiptFileIds.map { "Receipt for expense ${index + 1}: ${expense.description}" to it }
            }
        val ofWhole = declaration.receiptFileIds.map { "Receipt for the whole declaration" to it }
        return (ofExpenses + ofWhole).mapNotNull { (label, id) -> files[id]?.let { LabelledReceipt(label, it) } }
    }

    private data class LabelledReceipt(
        val label: String,
        val file: File,
    )

    /** Receipts added to [out] on pages of their own, each headed by what it is for. */
    private inner class ReceiptPages(
        private val out: PDDocument,
    ) {
        private val font = HtmlPdf.regularFont().use { PDType0Font.load(out, it) }

        fun add(receipt: LabelledReceipt) {
            val first = out.numberOfPages
            val added = if (receipt.file.mediaType == PDF) pdf(receipt.file) else picture(receipt.file)
            if (!added) out.addPage(PDPage(PDRectangle.A4))
            head(out.getPage(first), if (added) listOf(receipt.label) else listOf(receipt.label, MISSING))
        }

        private fun pdf(file: File): Boolean =
            try {
                val bytes = receipts.contentOf(file).use { it.readAllBytes() }
                Loader.loadPDF(bytes).use { PDFMergerUtility().appendDocument(out, it) }
                true
            } catch (e: IOException) {
                // An encrypted receipt, say, still gets its page saying where to find it.
                log.warn("Receipt {} could not be added to a declaration PDF", file.id, e)
                false
            }

        private fun picture(file: File): Boolean {
            val bitmap = pictures.bitmapOf(file) ?: return false
            val page = PDPage(PDRectangle.A4).also(out::addPage)
            draw(page, bitmap)
            return true
        }

        private fun draw(
            page: PDPage,
            bitmap: BufferedImage,
        ) {
            val image = JPEGFactory.createFromImage(out, bitmap, JPEG_QUALITY)
            val box = page.mediaBox
            val room = PDRectangle(MARGIN, MARGIN, box.width - 2 * MARGIN, box.height - 2 * MARGIN - LABEL_ROOM)
            val scale = minOf(room.width / image.width, room.height / image.height, 1f)
            val (width, height) = image.width * scale to image.height * scale
            PDPageContentStream(out, page, PDPageContentStream.AppendMode.APPEND, true, true).use {
                it.drawImage(image, room.lowerLeftX + (room.width - width) / 2, room.upperRightY - height, width, height)
            }
        }

        /** [lines] at the top of [page], above whatever it holds. */
        private fun head(
            page: PDPage,
            lines: List<String>,
        ) {
            PDPageContentStream(out, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.beginText()
                stream.setFont(font, LABEL_SIZE)
                stream.newLineAtOffset(MARGIN, page.mediaBox.upperRightY - MARGIN + LABEL_SIZE)
                lines.forEachIndexed { index, line ->
                    if (index > 0) stream.newLineAtOffset(0f, -LABEL_SIZE * LINE_HEIGHT)
                    stream.showText(line)
                }
                stream.endText()
            }
        }
    }

    private fun day(at: Instant) = at.atZone(ZONE).format(DAY)

    private fun euros(cents: Int) = "€ " + String.format(Locale.ROOT, "%.2f", cents / CENTS)

    // One receipt goes without saying; more are counted beside the expense.
    private fun receiptCount(count: Int) = if (count > 1) "$count receipts" else null

    private fun wholeReceipts(count: Int) =
        when (count) {
            0 -> null
            1 -> "1 receipt for the whole declaration"
            else -> "$count receipts for the whole declaration"
        }

    private companion object {
        val log = LoggerFactory.getLogger(DeclarationPdfs::class.java)
        const val MOST = 100
        const val TEMPLATE = "declarations/declaration-form"
        const val PDF = "application/pdf"
        const val STATEMENT = "They said the expenses were for Blueshell and the receipts are real."
        const val MISSING = "This receipt is not in this download. Open it on the website."
        const val CENTS = 100.0
        const val JPEG_QUALITY = 0.85f
        const val POINTS_PER_MM = 72f / 25.4f

        // The paper form's own margin.
        const val MARGIN = 18 * POINTS_PER_MM
        const val LABEL_SIZE = 10f
        const val LINE_HEIGHT = 1.4f
        const val LABEL_ROOM = 28f
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    }
}
