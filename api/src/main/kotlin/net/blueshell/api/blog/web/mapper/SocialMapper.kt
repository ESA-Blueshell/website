package net.blueshell.api.blog.web.mapper

import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.blog.web.dto.SocialDTO
import net.blueshell.api.blog.persistence.Blog
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SocialMapper {
    @Value($$"${frontend.url}")
    private lateinit var frontendUrl: String

    fun toSocialDTO(blog: Blog): SocialDTO {
        return SocialDTO().also { dto ->
            afterToSocialDTO(blog, dto)
        }
    }

    private fun afterToSocialDTO(blog: Blog, dto: SocialDTO) {
        dto.url = frontendUrl + "/blogs" + blog.id
        dto.title = blog.title
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
