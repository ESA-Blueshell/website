package net.blueshell.api.domain.blog.application

import org.jsoup.Jsoup
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

/**
 * Blog HTML is sanitised on the way *in*, before it is persisted, so this is a
 * write-side concern rather than a response-mapping one. It lived beside the
 * response mapper while the sanitising call sat in a command handler; with the
 * handler gone the layer rules make the misplacement visible.
 */
private val BLOG_HTML_SAFELIST: Safelist = Safelist.relaxed()
    .addAttributes(":all", "class")
    .addAttributes("a", "target", "rel")
    .addProtocols("a", "href", "http", "https", "mailto")

private val BLOG_HTML_CLEANER = Cleaner(BLOG_HTML_SAFELIST)

fun sanitizeBlogHtml(html: String): String {
    if (html.isBlank()) {
        return ""
    }
    val cleaned = BLOG_HTML_CLEANER.clean(Jsoup.parseBodyFragment(html))
    cleaned.select("div:has(a:matchesOwn((?i)unsubscribe))").remove()
    return cleaned.body().html().replace(">\\s+<".toRegex(), "><").trim()
}
