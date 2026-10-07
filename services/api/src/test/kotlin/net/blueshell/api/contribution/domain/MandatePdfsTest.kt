package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.MandatePdfDownloaded
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.user.api.AddressFields
import net.blueshell.api.user.api.OnlineMandates
import net.blueshell.api.user.api.OpenedOnlineMandate
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.Instant
import java.time.LocalDate

class MandatePdfsTest {
    private val mandates: OnlineMandates = mock()
    private val events: ApplicationEventPublisher = mock()
    private val templates =
        SpringTemplateEngine().apply {
            setTemplateResolver(
                ClassLoaderTemplateResolver().apply {
                    prefix = "templates/"
                    suffix = ".html"
                },
            )
        }
    private val pdfs = MandatePdfs(mandates, BankProperties(incassantId = "NL00 ZZZ 0000 0000 0000"), templates, events)

    private val known =
        OpenedOnlineMandate(
            userId = 12,
            username = "zoe",
            reference = "BLUESHELL-12-20260930",
            signedOn = LocalDate.of(2026, 9, 30),
            authorisedAt = Instant.parse("2026-09-30T10:15:00Z"),
            wordingVersion = "2026-10",
            wording = "I authorise ESA Blueshell to collect my yearly contribution from this account by incasso, and my bank to pay it.",
            accountHolder = "Zoë Bąkker",
            iban = "NL91ABNA0417164300",
            address = AddressFields("NL", "Enschede", "Hallenweg", "5", "7522NH"),
        )

    private fun textOf(bytes: ByteArray): String = Loader.loadPDF(bytes).use { PDFTextStripper().getText(it) }.replace(Regex("\\s+"), " ")

    @Test
    fun `prints a known online mandate as the mandate form, with the authorisation where the form has a signature`() {
        whenever(mandates.open(12)).thenReturn(known)

        val pdf = pdfs.pdf(12, downloadedBy = 3)
        val text = textOf(pdf.bytes)

        assertThat(pdf.name).isEqualTo("mandate-BLUESHELL-12-20260930.pdf")
        assertThat(text).contains(
            "SEPA",
            "Direct Debit Mandate",
            "Blueshell E-sports Vereniging Enschede",
            "Creditor Identification: NL00ZZZ000000000000",
            "BLUESHELL-12-20260930",
            "[X] recurrent",
            known.wording,
            "A refund must be claimed within 8 weeks",
            "Zoë Bąkker",
            "Hallenweg 5",
            "7522NH",
            "Enschede",
            "Netherlands",
            "NL91 ABNA 0417 1643 00",
            "Place: Online",
            "Date: 30 September 2026",
            "Authorised online on 30 September 2026 at 12:15 by Zoë Bąkker, signed in as zoe",
            "Online mandate V2.0, October 2026. Authorisation wording 2026-10.",
        )
        assertThat(text).doesNotContain("BIC")
        verify(events).publishEvent(MandatePdfDownloaded(userId = 12, reference = "BLUESHELL-12-20260930", downloadedBy = 3))
        assertThat(known.toString()).doesNotContain("NL91").doesNotContain("Hallenweg")
    }

    @Test
    fun `names a country it has no name for by its code`() {
        whenever(mandates.open(12)).thenReturn(known.copy(address = known.address.copy(country = "")))

        assertThat(textOf(pdfs.pdf(12, downloadedBy = 3).bytes)).contains("Country:")
    }
}
