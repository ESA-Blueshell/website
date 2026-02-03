package net.blueshell.api.mapper

import net.blueshell.api.common.enums.PlatformType
import net.blueshell.api.dto.SocialDTO
import net.blueshell.api.model.Blog
import org.mapstruct.AfterMapping
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.MappingTarget
import org.springframework.beans.factory.annotation.Value

@Mapper(componentModel = "spring")
abstract class SocialMapper {
    @Value("\${frontend.url}")
    private val frontendUrl: String = null

    @BeanMapping(ignoreByDefault = true)
    abstract fun toSocialDTO(blog: Blog): SocialDTO

    @AfterMapping
    protected fun afterToSocialDTO(blog: Blog, @MappingTarget dto: SocialDTO) {
        dto.setUrl(frontendUrl + "/blogs" + blog.getId())
        dto.setTitle(blog.getTitle())
        dto.setText(dto.getText())
        val platforms = arrayOf<PlatformType>(
            PlatformType.FACEBOOK,
            PlatformType.TWITTER,
            PlatformType.INSTAGRAM,
            PlatformType.LINKEDIN
        )
        dto.setPlatforms(platforms)
    }
}
