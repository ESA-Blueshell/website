package net.blueshell.api.blog.web.mapping

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.blog.persistence.Blog
import net.blueshell.api.blog.web.dto.BlogDTO
import net.blueshell.api.blog.web.dto.SocialDTO
import org.jsoup.Jsoup
import java.time.Instant

@Konverter
interface BlogKonverter {
    fun toDTO(blog: Blog): BlogDTO

    fun fromDTO(dto: BlogDTO): Blog
}

@Konverter
interface SocialKonverter {
    fun toSocialDTO(blog: Blog): SocialDTO
}

private val blogKonverter = Konverter.get<BlogKonverter>()
private val socialKonverter = Konverter.get<SocialKonverter>()

fun Blog.asDto(frontendUrl: String): BlogDTO {
    val dto = blogKonverter.toDTO(this)
    dto.url = "$frontendUrl/blogs/${id}"
    return dto
}

fun Blog.asSocialDto(frontendUrl: String): SocialDTO {
    val dto = socialKonverter.toSocialDTO(this)
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
    val mapped = blogKonverter.fromDTO(this)
    blog.title = mapped.title
    blog.publishedAt = mapped.publishedAt ?: Instant.now()
    blog.version = mapped.version
    sanitizeHtml(this, blog)
    return blog
}

private fun sanitizeHtml(dto: BlogDTO, blog: Blog) {
    val content = dto.html
    if (content != null && content.trim { it <= ' ' }.isNotEmpty()) {
        val doc = Jsoup.parse(content)
        doc.select("div:has(a:contains(Unsubscribe))").remove()
        blog.html = doc.html().replace(">\\s+<".toRegex(), "><").trim { it <= ' ' }
    }
}
