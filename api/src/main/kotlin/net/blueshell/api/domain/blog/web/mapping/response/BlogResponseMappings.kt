package net.blueshell.api.domain.blog.web.mapping.response

import net.blueshell.api.domain.blog.persistence.Blog
import net.blueshell.api.domain.blog.web.dto.response.BlogResponse
import org.jsoup.Jsoup
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

private val BLOG_HTML_SAFELIST: Safelist = Safelist.relaxed()
    .addAttributes(":all", "class")
    .addAttributes("a", "target", "rel")
    .addProtocols("a", "href", "http", "https", "mailto")

private val BLOG_HTML_CLEANER = Cleaner(BLOG_HTML_SAFELIST)

fun Blog.asResponse(frontendUrl: String): BlogResponse =
    BlogResponse(
        id = this.id!!,
        url = "$frontendUrl/blogs/${this.id!!}",
        title = this.title,
        html = this.html,
        publishedAt = this.publishedAt,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

private fun sanitizeHtml(content: String): String {
    if (content.isBlank()) {
        return ""
    }
    val cleaned = BLOG_HTML_CLEANER.clean(Jsoup.parseBodyFragment(content))
    cleaned.select("div:has(a:matchesOwn((?i)unsubscribe))").remove()
    return cleaned.body().html().replace(">\\s+<".toRegex(), "><").trim()
}

fun sanitizeBlogHtml(html: String): String = sanitizeHtml(html)
