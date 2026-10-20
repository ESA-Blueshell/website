package net.blueshell.api.declaration.domain

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.declaration.persistence.DeclarationRepository
import net.blueshell.api.declaration.persistence.DeclarationState
import net.blueshell.api.declaration.persistence.DeclarationStep
import net.blueshell.api.file.api.PictureBitmaps
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.BankAccountLookup
import net.blueshell.api.user.api.MaskedIban
import net.blueshell.api.user.api.UserService
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.text.PDFTextStripper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.util.Optional

class DeclarationPdfsTest {
    private val repository: DeclarationRepository = mockk()
    private val receipts: Receipts = mockk()
    private val pictures: PictureBitmaps = mockk()
    private val users: UserService = mockk()
    private val bankAccounts: BankAccountLookup = mockk()
    private val templates =
        SpringTemplateEngine().apply {
            setTemplateResolver(
                ClassLoaderTemplateResolver().apply {
                    prefix = "templates/"
                    suffix = ".html"
                },
            )
        }
    private val reviews = DeclarationReviews(repository, mockk(), bankAccounts, Clock.systemUTC())
    private val pdfs = DeclarationPdfs(reviews, receipts, pictures, users, bankAccounts, templates)

    private val photo = Entities.file(501, type = FileType.RECEIPT)
    private val scan = Entities.file(502, type = FileType.RECEIPT, name = "shop.pdf", mediaType = "application/pdf")
    private val locked = Entities.file(503, type = FileType.RECEIPT, name = "locked.pdf", mediaType = "application/pdf")
    private val broken = Entities.file(504, type = FileType.RECEIPT)

    private val paid =
        declarationOf(40, DeclarationState.PAID, "Pizza for the committee meeting" to 1250, "Cups" to 250).apply {
            expenses[0].receiptFileIds += listOf(501L, 502L)
            receiptFileIds += listOf(503L, 504L)
            steps += DeclarationStep(DeclarationState.APPROVED, Instant.parse("2026-10-19T10:00:00Z"), 3)
            steps += DeclarationStep(DeclarationState.PAID, Instant.parse("2026-10-20T10:00:00Z"), 3, paidTo = "NL00")
        }
    private val rejected =
        declarationOf(41, DeclarationState.REJECTED, "Snacks" to 500).apply {
            receiptFileIds += 501L
            steps += DeclarationStep(DeclarationState.REJECTED, Instant.parse("2026-10-19T10:00:00Z"), null, "Not for the association.")
        }

    init {
        every { repository.findById(any()) } returns Optional.empty()
        every { repository.findById(40) } returns Optional.of(paid)
        every { repository.findById(41) } returns Optional.of(rejected)
        // Approved once, sent back and submitted again: it waits to be approved anew.
        every { repository.findById(42) } returns
            Optional.of(
                declarationOf(42, DeclarationState.SUBMITTED, "Cables" to 3999).apply {
                    steps += DeclarationStep(DeclarationState.APPROVED, Instant.parse("2026-10-17T10:00:00Z"), 3)
                    steps += DeclarationStep(DeclarationState.DRAFT, Instant.parse("2026-10-18T10:00:00Z"), 3, "Cut off")
                    steps += DeclarationStep(DeclarationState.SUBMITTED, Instant.parse("2026-10-19T10:00:00Z"), 12)
                },
            )
        every { users.findAllByIds(any()) } returns listOf(Entities.user(12, firstName = "Ann", lastName = "Vos"), Entities.user(3))
        every { bankAccounts.preview(12) } returns MaskedIban("DE", "21")
        val stored = mapOf(501L to photo, 502L to scan, 503L to locked, 504L to broken)
        every { receipts.describe(any()) } answers { firstArg<Collection<Long>>().associateWith(stored::getValue) }
        every { receipts.contentOf(scan) } answers { onePagePdf().inputStream() }
        every { receipts.contentOf(locked) } answers { "%PDF-1.7 not really".byteInputStream() }
        every { pictures.bitmapOf(photo) } returns BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB)
        every { pictures.bitmapOf(broken) } returns null
    }

    private fun onePagePdf(): ByteArray =
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            ByteArrayOutputStream().also(doc::save).toByteArray()
        }

    private fun pagesOf(bytes: ByteArray): List<String> =
        Loader.loadPDF(bytes).use { doc ->
            (1..doc.numberOfPages).map { page ->
                PDFTextStripper()
                    .apply {
                        startPage = page
                        endPage = page
                    }.getText(doc)
                    .replace(Regex("\\s+"), " ")
            }
        }

    @Test
    fun `prints a declaration as the paper form, then each receipt on pages of its own with what it is for`() {
        val pdf = pdfs.pdf(listOf(40))
        val pages = pagesOf(pdf.bytes)

        assertThat(pdf.name).isEqualTo("declaration-40.pdf")
        assertThat(pages).hasSize(5)
        assertThat(pages[0]).contains(
            "Declaration form Blueshell E-Sports Vereniging Enschede",
            "Submitted by Ann Vos",
            "Bastille 49, Postbus 217",
            "2026-10-01 Pizza for the committee meeting 2 receipts",
            "€ 12.50",
            "Total € 15.00",
            "2 receipts for the whole declaration",
            "Submitted on 1 January 1970 by Ann Vos. They said the expenses were for Blueshell and the receipts are real.",
            "Approved on 19 October 2026 by User 3. Paid on 20 October 2026 to NL",
        )
        // Paid, it names the account it went to, never the one on file now, and only as its preview.
        assertThat(pages[0]).doesNotContain("DE").contains("••00")
        assertThat(pages[1]).contains("Receipt for expense 1: Pizza for the committee meeting")
        assertThat(pages[2]).contains("Receipt for expense 1: Pizza for the committee meeting")
        for (missing in pages.drop(3)) {
            assertThat(missing)
                .contains("Receipt for the whole declaration", "This receipt is not in this download. Open it on the website.")
        }
    }

    @Test
    fun `prints several declarations as one PDF, each with the account on file now unless it was paid`() {
        val pdf = pdfs.pdf(listOf(41, 42, 41))
        val pages = pagesOf(pdf.bytes)

        assertThat(pdf.name).isEqualTo("declarations.pdf")
        assertThat(pages).hasSize(3)
        assertThat(pages[0]).contains("1 receipt for the whole declaration")
        assertThat(pages[0]).contains("Rejected on 19 October 2026 by a deleted account: Not for the association.", "DE•• … ••21")
        assertThat(pages[2]).contains("Cables", "€ 39.99", "Submitted on 19 October 2026 by Ann Vos.", "Not approved yet.")

        every { bankAccounts.preview(12) } returns null
        assertThat(pagesOf(pdfs.pdf(listOf(42)).bytes).single()).contains("IBAN Not added")
    }

    @Test
    fun `prints only what reached the board, between one and a hundred at once`() {
        assertThatThrownBy { pdfs.pdf(listOf(99)) }.isInstanceOf(DeclarationNotFound::class.java)
        assertThatThrownBy { pdfs.pdf(emptyList()) }.isInstanceOf(PdfSelectionSize::class.java)
        assertThatThrownBy { pdfs.pdf((1L..101L).toList()) }
            .isInstanceOf(PdfSelectionSize::class.java)
            .extracting("facts")
            .isEqualTo(mapOf("most" to 100))
    }
}
