package net.blueshell.api.contribution.domain

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import net.blueshell.api.contribution.api.MandatePdfDownloaded
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.user.api.OnlineMandates
import net.blueshell.api.user.api.OpenedOnlineMandate
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** An online mandate as a PDF, named for its reference. */
class MandatePdf(
    val name: String,
    val bytes: ByteArray,
)

/**
 * An online mandate printed as the association's direct debit mandate form, built from the sealed
 * mandate each time it is asked for and kept nowhere. It follows the paper form of August 2021,
 * with no BIC row, the incassant id as the creditor identification, and the authorisation the
 * member gave on the site where the paper form has a signature.
 */
@Service
class MandatePdfs(
    private val mandates: OnlineMandates,
    private val bank: BankProperties,
    private val templates: TemplateEngine,
    private val events: ApplicationEventPublisher,
) {
    /** The PDF for [downloadedBy], whose download is written to the member's security log before it is answered. */
    @Transactional(readOnly = true)
    fun pdf(
        membershipId: Long,
        downloadedBy: Long,
    ): MandatePdf {
        val mandate = mandates.open(membershipId)
        val bytes = render(templates.process("mandates/online-mandate", Context(Locale.ENGLISH, fields(mandate))))
        events.publishEvent(MandatePdfDownloaded(mandate.userId, membershipId, downloadedBy))
        return MandatePdf("mandate-${mandate.reference}.pdf", bytes)
    }

    private fun fields(mandate: OpenedOnlineMandate): Map<String, Any> {
        val authorised = mandate.authorisedAt.atZone(ZONE)
        val day = authorised.format(DAY)
        return mapOf(
            "creditorId" to bank.incassantId.replace(" ", ""),
            "reference" to mandate.reference,
            "wording" to mandate.wording,
            "accountHolder" to mandate.accountHolder,
            "street" to listOfNotNull(mandate.address.street, mandate.address.houseNumber).joinToString(" "),
            "zipCode" to mandate.address.zipCode.orEmpty(),
            "city" to mandate.address.city.orEmpty(),
            "country" to countryName(mandate.address.country.orEmpty()),
            "iban" to mandate.iban.chunked(IBAN_GROUP).joinToString(" "),
            "authorisedOn" to day,
            "authorisation" to
                "Authorised online on $day at ${authorised.format(TIME)} by ${mandate.accountHolder}, signed in as ${mandate.username}",
            "footer" to "$TEMPLATE_VERSION. Authorisation wording ${mandate.wordingVersion}.",
        )
    }

    private fun countryName(code: String): String = Locale.of("", code).getDisplayCountry(Locale.ENGLISH).ifBlank { code }

    private fun render(html: String): ByteArray =
        ByteArrayOutputStream().use { out ->
            PdfRendererBuilder()
                .useFont({ font("BarlowSemiCondensed-Regular.ttf") }, FONT, REGULAR, FontStyle.NORMAL, true)
                .useFont({ font("BarlowSemiCondensed-Bold.ttf") }, FONT, BOLD, FontStyle.NORMAL, true)
                .withHtmlContent(html, requireNotNull(javaClass.getResource(ASSETS)).toExternalForm())
                .toStream(out)
                .run()
            out.toByteArray()
        }

    private fun font(file: String) =
        requireNotNull(javaClass.getResourceAsStream("${ASSETS}fonts/$file")) { "The font $file is not packaged" }

    private companion object {
        const val TEMPLATE_VERSION = "Online mandate V2.0, October 2026"
        const val ASSETS = "/templates/assets/"
        const val FONT = "Barlow"
        const val REGULAR = 400
        const val BOLD = 700
        const val IBAN_GROUP = 4
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    }
}
