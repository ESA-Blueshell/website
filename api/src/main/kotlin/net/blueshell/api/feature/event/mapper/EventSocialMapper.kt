package net.blueshell.api.feature.event.mapper

import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.feature.blog.dto.SocialDTO
import net.blueshell.api.feature.event.dto.EventDTO
import org.mapstruct.*

@Mapper(componentModel = "spring")
abstract class EventSocialMapper {
    @Mapping(source = "description", target = "text")
    @BeanMapping(ignoreByDefault = true)
    abstract fun toSocialDto(dto: EventDTO): SocialDTO

    @AfterMapping
    fun afterToSocialDTO(dto: EventDTO, @MappingTarget socialDTO: SocialDTO) {
        val platforms = arrayOf(PlatformType.FACEBOOK, PlatformType.TWITTER, PlatformType.INSTAGRAM)
        socialDTO.platforms = platforms
    }
}
