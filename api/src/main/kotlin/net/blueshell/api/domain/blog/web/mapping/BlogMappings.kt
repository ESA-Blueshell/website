package net.blueshell.api.domain.blog.web.mapping

import net.blueshell.api.domain.blog.persistence.Blog
import net.blueshell.api.domain.blog.web.dto.BlogResponse
import net.blueshell.api.domain.blog.web.dto.CreateBlogRequest
import net.blueshell.api.domain.blog.web.dto.SocialDTO
import net.blueshell.api.domain.blog.web.dto.UpdateBlogRequest
import net.blueshell.api.shared.enums.PlatformType
import org.jsoup.Jsoup
import tech.mappie.api.ObjectMappie

object BlogToBlogResponseMapper : ObjectMappie<Blog, BlogResponse>()

object BlogToSocialDTOMapper : ObjectMappie<Blog, SocialDTO>()

fun Blog.asResponse(frontendUrl: String): BlogResponse {
    val response = BlogToBlogResponseMapper.map(this)
    response.url = "$frontendUrl/blogs/${id}"
    return response
}

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

fun CreateBlogRequest.asEntity(blog: Blog = Blog()): Blog {
    blog.title = title!!
    blog.publishedAt = publishedAt!!
    sanitizeHtml(this, blog)
    return blog
}

fun UpdateBlogRequest.asEntity(blog: Blog = Blog()): Blog {
    blog.title = title!!
    blog.publishedAt = publishedAt!!
    version?.let { blog.version = it }
    sanitizeHtml(this, blog)
    return blog
}

private fun sanitizeHtml(dto: CreateBlogRequest, blog: Blog) {
    val content = dto.html!!
    if (content.trim { it <= ' ' }.isNotEmpty()) {
        val doc = Jsoup.parse(content)
        doc.select("div:has(a:contains(Unsubscribe))").remove()
        blog.html = doc.html().replace(">\\s+<".toRegex(), "><").trim { it <= ' ' }
    }
}

private fun sanitizeHtml(dto: UpdateBlogRequest, blog: Blog) {
    val content = dto.html!!
    if (content.trim { it <= ' ' }.isNotEmpty()) {
        val doc = Jsoup.parse(content)
        doc.select("div:has(a:contains(Unsubscribe))").remove()
        blog.html = doc.html().replace(">\\s+<".toRegex(), "><").trim { it <= ' ' }
    }
}
