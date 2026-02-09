package net.blueshell.api.event.web.mapper

import net.blueshell.api.event.web.dto.EventBannerDTO
import net.blueshell.api.file.web.mapper.FileMapper
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.event.persistence.EventBanner
import org.springframework.stereotype.Component

@Component
class EventBannerMapper(
    private val fileMapper: FileMapper
) : BaseMapper<EventBanner, EventBannerDTO>() {
    override fun fromDTO(dto: EventBannerDTO): EventBanner = fromDTO(dto, EventBanner())

    fun fromDTO(dto: EventBannerDTO, banner: EventBanner): EventBanner {
        banner.file = fileMapper.fromDTO(requireNotNull(dto.file))
        dto.version?.let { banner.version = it }
        return banner
    }

    override fun toDTO(entity: EventBanner): EventBannerDTO {
        return EventBannerDTO(
            file = entity.file.let { fileMapper.toDTO(it) }
        ).also { dto ->
            dto.version = entity.version
        }
    }
}

fun EventBanner.asDTO(mapper: EventBannerMapper): EventBannerDTO = mapper.toDTO(this)

fun EventBannerDTO.asEntity(mapper: EventBannerMapper): EventBanner = mapper.fromDTO(this)
