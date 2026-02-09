package net.blueshell.api.blog.api.mapper

import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.blog.api.dto.SocialDTO
import net.blueshell.api.blog.domain.model.Blog
import org.mapstruct.AfterMapping
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.MappingTarget
import org.springframework.beans.factory.annotation.Value

@Mapper(componentModel = "spring")
abstract class SocialMapper {
    @Value($$"${frontend.url}")
    private lateinit var frontendUrl: String

    @BeanMapping(ignoreByDefault = true)
    abstract fun toSocialDTO(blog: Blog): SocialDTO

    @AfterMapping
    protected fun afterToSocialDTO(blog: Blog, @MappingTarget dto: SocialDTO) {
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
