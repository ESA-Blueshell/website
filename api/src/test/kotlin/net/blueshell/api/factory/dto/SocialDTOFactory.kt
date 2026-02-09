package net.blueshell.api.factory.dto

import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.blog.web.dto.SocialDTO
import org.springframework.stereotype.Component

/**
 * Factory for SocialDTO test instances.
 */
@Component
class SocialDTOFactory : BaseDtoFactory<SocialDTO>() {

    override fun targetType(): Class<SocialDTO> = SocialDTO::class.java

    override fun createBasic(): SocialDTO {
        val dto = SocialDTO()
        dto.title = "Hello world"
        dto.text = "Body"
        dto.url = "https://example.com/${nextId()}"
        dto.platforms = arrayOf(PlatformType.FACEBOOK)
        return dto
    }
}
