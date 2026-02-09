package net.blueshell.api.event.web.mapper

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.blog.web.dto.SocialDTO
import net.blueshell.api.event.web.dto.EventDTO
import org.springframework.stereotype.Component

@Konverter
interface EventSocialKonverter {
    fun toSocialDto(dto: EventDTO): SocialDTO
}

@Component
class EventSocialMapper {
    private val konverter = Konverter.get<EventSocialKonverter>()

    fun toSocialDto(dto: EventDTO): SocialDTO {
        val socialDTO = konverter.toSocialDto(dto)
        afterToSocialDTO(dto, socialDTO)
        return socialDTO
    }

    private fun afterToSocialDTO(dto: EventDTO, socialDTO: SocialDTO) {
        socialDTO.text = dto.description
        val platforms = arrayOf(PlatformType.FACEBOOK, PlatformType.TWITTER, PlatformType.INSTAGRAM)
        socialDTO.platforms = platforms
    }
}
