package net.blueshell.api.blog.web.mapping

import net.blueshell.api.blog.persistence.Blog
import net.blueshell.api.blog.web.dto.BlogDTO
import net.blueshell.api.blog.web.dto.SocialDTO
import net.blueshell.api.shared.enums.PlatformType
import org.jsoup.Jsoup
import tech.mappie.api.ObjectMappie

object BlogToBlogDTOMapper : ObjectMappie<Blog, BlogDTO>()

object BlogToSocialDTOMapper : ObjectMappie<Blog, SocialDTO>()

fun Blog.asDto(frontendUrl: String): BlogDTO {
    val dto = BlogToBlogDTOMapper.map(this)
    dto.url = "$frontendUrl/blogs/${id}"
    return dto
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

fun BlogDTO.asEntity(blog: Blog = Blog()): Blog {
    blog.title = title!!
    blog.publishedAt = publishedAt!!
    blog.version = version!!
    sanitizeHtml(this, blog)
    return blog
}

private fun sanitizeHtml(dto: BlogDTO, blog: Blog) {
    val content = dto.html!!
    if (content.trim { it <= ' ' }.isNotEmpty()) {
        val doc = Jsoup.parse(content)
        doc.select("div:has(a:contains(Unsubscribe))").remove()
        blog.html = doc.html().replace(">\\s+<".toRegex(), "><").trim { it <= ' ' }
    }
}
