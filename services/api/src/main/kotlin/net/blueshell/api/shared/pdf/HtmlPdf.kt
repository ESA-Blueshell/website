package net.blueshell.api.shared.pdf

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * A filled-in template rendered to PDF in the site's face, Barlow, which the template names as
 * `'Barlow'`. The renderer reads CSS 2.1, so a template lays out with tables, and its images
 * resolve against the packaged assets.
 */
object HtmlPdf {
    fun render(html: String): ByteArray =
        ByteArrayOutputStream().use { out ->
            PdfRendererBuilder()
                .useFont({ regularFont() }, FONT, REGULAR, FontStyle.NORMAL, true)
                .useFont({ font(BOLD_FILE) }, FONT, BOLD, FontStyle.NORMAL, true)
                .withHtmlContent(html, requireNotNull(javaClass.getResource(ASSETS)).toExternalForm())
                .toStream(out)
                .run()
            out.toByteArray()
        }

    /** The regular face, for text drawn on a page outside a template. The caller closes it. */
    fun regularFont(): InputStream = font(REGULAR_FILE)

    private fun font(file: String) =
        requireNotNull(javaClass.getResourceAsStream("${ASSETS}fonts/$file")) { "The font $file is not packaged" }

    private const val ASSETS = "/templates/assets/"
    private const val FONT = "Barlow"
    private const val REGULAR_FILE = "BarlowSemiCondensed-Regular.ttf"
    private const val BOLD_FILE = "BarlowSemiCondensed-Bold.ttf"
    private const val REGULAR = 400
    private const val BOLD = 700
}
