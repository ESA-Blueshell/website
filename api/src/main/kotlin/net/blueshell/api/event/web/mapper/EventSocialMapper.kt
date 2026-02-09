package net.blueshell.api.event.web.mapper

import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.blog.web.dto.SocialDTO
import net.blueshell.api.event.web.dto.EventDTO
import org.springframework.stereotype.Component

@Component
class EventSocialMapper {
    fun toSocialDto(dto: EventDTO): SocialDTO {
        return SocialDTO(text = dto.description).also { socialDTO ->
            afterToSocialDTO(dto, socialDTO)
        }
    }

    private fun afterToSocialDTO(dto: EventDTO, socialDTO: SocialDTO) {
        val platforms = arrayOf(PlatformType.FACEBOOK, PlatformType.TWITTER, PlatformType.INSTAGRAM)
        socialDTO.platforms = platforms
    }
}
