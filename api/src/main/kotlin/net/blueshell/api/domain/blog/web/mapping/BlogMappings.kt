package net.blueshell.api.domain.blog.web.mapping

import net.blueshell.api.domain.blog.persistence.Blog
import net.blueshell.api.domain.blog.web.dto.BlogResponse
import net.blueshell.api.domain.blog.web.dto.SocialDTO
import net.blueshell.api.shared.enums.PlatformType
import org.jsoup.Jsoup
import tech.mappie.api.ObjectMappie

private data class BlogResponseSource(
    val blog: Blog,
    val frontendUrl: String
)

object BlogResponseSourceToBlogResponseMapper : ObjectMappie<BlogResponseSource, BlogResponse>() {
    override fun map(from: BlogResponseSource) = mapping {
        BlogResponse::id fromProperty { from.blog.id }
        BlogResponse::version fromProperty { from.blog.version }
        BlogResponse::createdAt fromProperty { from.blog.createdAt }
        BlogResponse::updatedAt fromProperty { from.blog.updatedAt }
        BlogResponse::deletedAt fromProperty { from.blog.deletedAt }
        BlogResponse::title fromProperty { from.blog.title }
        BlogResponse::html fromProperty { from.blog.html }
        BlogResponse::publishedAt fromProperty { from.blog.publishedAt }
        BlogResponse::url fromProperty { "${from.frontendUrl}/blogs/${from.blog.id}" }
    }
}

object BlogToSocialDTOMapper : ObjectMappie<Blog, SocialDTO>()

fun Blog.asResponse(frontendUrl: String): BlogResponse =
    BlogResponseSourceToBlogResponseMapper.map(BlogResponseSource(this, frontendUrl))

fun Blog.asSocialDto(frontendUrl: String): SocialDTO {
    val dto = BlogToSocialDTOMapper.map(this)
    dto.url = "$frontendUrl/blogs$id"
    dto.platforms = arrayOf(
        PlatformType.FACEBOOK,
        PlatformType.TWITTER,
        PlatformType.INSTAGRAM,
        PlatformType.LINKEDIN
    )
    return dto
}

private fun sanitizeHtml(content: String): String {
    if (content.trim { it <= ' ' }.isEmpty()) {
        return ""
    }
    val doc = Jsoup.parse(content)
    doc.select("div:has(a:contains(Unsubscribe))").remove()
    return doc.html().replace(">\\s+<".toRegex(), "><").trim { it <= ' ' }
}

fun sanitizeBlogHtml(html: String): String = sanitizeHtml(html)
