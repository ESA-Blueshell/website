package net.blueshell.api.mapper.event

import net.blueshell.api.common.enums.PlatformType
import net.blueshell.api.dto.SocialDTO
import net.blueshell.api.dto.event.EventDTO
import org.mapstruct.*

@Mapper(componentModel = "spring")
abstract class EventSocialMapper {
    @Mapping(source = "description", target = "text")
    @BeanMapping(ignoreByDefault = true)
    abstract fun toSocialDto(dto: EventDTO?): SocialDTO?

    @AfterMapping
    fun afterToSocialDTO(dto: EventDTO?, @MappingTarget socialDTO: SocialDTO) {
        val platforms = arrayOf<PlatformType?>(PlatformType.FACEBOOK, PlatformType.TWITTER, PlatformType.INSTAGRAM)
        socialDTO.setPlatforms(platforms)
    }
}
