package net.blueshell.api.blog.web.mapper

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.blog.web.dto.SocialDTO
import net.blueshell.api.blog.persistence.Blog
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Konverter
interface SocialKonverter {
    fun toSocialDTO(blog: Blog): SocialDTO
}

@Component
class SocialMapper {
    @Value($$"${frontend.url}")
    private lateinit var frontendUrl: String
    private val konverter = Konverter.get<SocialKonverter>()

    fun toSocialDTO(blog: Blog): SocialDTO {
        val dto = konverter.toSocialDTO(blog)
        afterToSocialDTO(blog, dto)
        return dto
    }

    private fun afterToSocialDTO(blog: Blog, dto: SocialDTO) {
        dto.url = frontendUrl + "/blogs" + blog.id
        dto.text = dto.text
        val platforms = arrayOf(
            PlatformType.FACEBOOK,
            PlatformType.TWITTER,
            PlatformType.INSTAGRAM,
            PlatformType.LINKEDIN
        )
        dto.platforms = platforms
    }
}

fun Blog.asDTO(mapper: SocialMapper): SocialDTO = mapper.toSocialDTO(this)
