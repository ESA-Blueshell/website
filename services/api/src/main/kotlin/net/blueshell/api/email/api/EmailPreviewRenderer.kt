package net.blueshell.api.email.api

import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.model.RenderedEmailPreview
import org.springframework.stereotype.Service
import java.util.Base64

/**
 * Renders an email for inspection: the same HTML the send path produces, with the hosted
 * images replaced by inline data so the preview shows them.
 *
 * Nothing here writes, enqueues or sends. A caller that has an [EmailContent] can preview
 * it whatever built it, so this stays free of any one flow's vocabulary.
 */
@Service
class EmailPreviewRenderer(private val emailSender: EmailSenderService) {

    fun render(content: EmailContent): RenderedEmailPreview =
        RenderedEmailPreview(
            subject = content.subject,
            html = inlineEmailAssets(emailSender.renderEmailHtml(content)),
        )

    /**
     * Preview-only: swap the hosted email-asset URLs for base64 data read from the
     * classpath, so the logo and watermark appear whether or not the configured frontend
     * URL is reachable from the operator's browser — a docker-internal hostname in dev, or
     * assets not yet deployed. The send path keeps the hosted URLs, which is what a mail
     * client wants.
     */
    private fun inlineEmailAssets(html: String): String {
        var result = html
        INLINEABLE_ASSETS.forEach { (urlSuffix, dataUri) ->
            if (dataUri != null) result = replaceUrlsEndingWith(result, urlSuffix, dataUri)
        }
        return result
    }

    /**
     * Replace every URL ending in [suffix] — in `src`, `background` or a CSS `url(...)` —
     * with [replacement].
     *
     * Deliberately not a regex. A pattern like `[^"'()\s]*suffix` backtracks quadratically
     * once the first replacement has inserted a ~180KB base64 token with no delimiters in
     * it, which took a single preview from milliseconds to minutes. This is a linear scan:
     * find the suffix, walk back to the URL's start delimiter, splice.
     */
    private fun replaceUrlsEndingWith(html: String, suffix: String, replacement: String): String {
        var hit = html.indexOf(suffix)
        if (hit < 0) return html
        val sb = StringBuilder(html.length + replacement.length)
        var emitted = 0
        while (hit >= 0) {
            var start = hit
            while (start > emitted && html[start - 1] !in URL_DELIMITERS) start--
            sb.append(html, emitted, start).append(replacement)
            emitted = hit + suffix.length
            hit = html.indexOf(suffix, emitted)
        }
        sb.append(html, emitted, html.length)
        return sb.toString()
    }

    private companion object {
        /** Characters that terminate a URL token when scanning backwards from the suffix. */
        val URL_DELIMITERS = setOf('"', '\'', '(', ')', '>', '=', ',', ' ', '\t', '\n', '\r')

        /** Hosted-URL suffix to data URI; null when the classpath asset is missing. */
        val INLINEABLE_ASSETS: Map<String, String?> by lazy {
            mapOf(
                "/img/email/blueshell-logo.png" to classpathDataUri("templates/assets/BSLOGO.png"),
                "/img/email/watermark.png" to classpathDataUri("templates/assets/BackdropBlack.png"),
            )
        }

        fun classpathDataUri(path: String): String? =
            EmailPreviewRenderer::class.java.classLoader.getResourceAsStream(path)?.use {
                "data:image/png;base64," + Base64.getEncoder().encodeToString(it.readBytes())
            }
    }
}
