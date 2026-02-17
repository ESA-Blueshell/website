package net.blueshell.api.domain.blog.web.mapping.response

import net.blueshell.api.domain.blog.persistence.Blog
import net.blueshell.api.domain.blog.web.dto.response.BlogResponse
import org.jsoup.Jsoup
import tech.mappie.api.ObjectMappie

internal data class BlogResponseSource(
    val blog: Blog,
    val frontendUrl: String
)

internal object BlogResponseSourceToBlogResponseMapper : ObjectMappie<BlogResponseSource, BlogResponse>() {
    override fun map(from: BlogResponseSource): BlogResponse {
        val computedUrl = "${from.frontendUrl}/blogs/${from.blog.id}"
        return mapping {
            BlogResponse::id fromValue from.blog.id
            BlogResponse::version fromValue from.blog.version
            BlogResponse::createdAt fromValue from.blog.createdAt
            BlogResponse::updatedAt fromValue from.blog.updatedAt
            BlogResponse::deletedAt fromValue from.blog.deletedAt
            BlogResponse::title fromValue from.blog.title
            BlogResponse::html fromValue from.blog.html
            BlogResponse::publishedAt fromValue from.blog.publishedAt
            BlogResponse::url fromValue computedUrl
        }
    }
}

fun Blog.asResponse(frontendUrl: String): BlogResponse =
    BlogResponseSourceToBlogResponseMapper.map(BlogResponseSource(this, frontendUrl))

private fun sanitizeHtml(content: String): String {
    if (content.trim { it <= ' ' }.isEmpty()) {
        return ""
    }
    val doc = Jsoup.parse(content)
    doc.select("div:has(a:contains(Unsubscribe))").remove()
    return doc.html().replace(">\\s+<".toRegex(), "><").trim { it <= ' ' }
}

fun sanitizeBlogHtml(html: String): String = sanitizeHtml(html)
