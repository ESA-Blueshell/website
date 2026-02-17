package net.blueshell.api.domain.blog.web.mapping.response

import net.blueshell.api.domain.blog.persistence.Blog
import net.blueshell.api.domain.blog.web.dto.response.BlogResponse
import org.jsoup.Jsoup

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
    if (content.trim { it <= ' ' }.isEmpty()) {
        return ""
    }
    val doc = Jsoup.parse(content)
    doc.select("div:has(a:contains(Unsubscribe))").remove()
    return doc.html().replace(">\\s+<".toRegex(), "><").trim { it <= ' ' }
}

fun sanitizeBlogHtml(html: String): String = sanitizeHtml(html)
