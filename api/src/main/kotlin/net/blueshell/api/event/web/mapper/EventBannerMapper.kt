package net.blueshell.api.event.web.mapper

import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping
import net.blueshell.api.event.web.dto.EventBannerDTO
import net.blueshell.api.file.web.mapper.FileMapper
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.event.persistence.EventBanner
import org.springframework.stereotype.Component

@Konverter
interface EventBannerKonverter {
    @Konvert(mappings = [Mapping(target = "file", ignore = true)])
    fun toDTO(banner: EventBanner): EventBannerDTO

    @Konvert(mappings = [Mapping(target = "file", ignore = true)])
    fun fromDTO(dto: EventBannerDTO): EventBanner
}

@Component
class EventBannerMapper(
    private val fileMapper: FileMapper
) : BaseMapper<EventBanner, EventBannerDTO>() {
    private val konverter = konverter<EventBannerKonverter>()

    override fun fromDTO(dto: EventBannerDTO): EventBanner = fromDTO(dto, EventBanner())

    fun fromDTO(dto: EventBannerDTO, banner: EventBanner): EventBanner {
        banner.file = fileMapper.fromDTO(requireNotNull(dto.file))
        dto.version?.let { banner.version = it }
        return banner
    }

    override fun toDTO(entity: EventBanner): EventBannerDTO {
        val dto = konverter.toDTO(entity)
        dto.file = entity.file.let { fileMapper.toDTO(it) }
        return dto
    }
}

fun EventBanner.asDTO(mapper: EventBannerMapper): EventBannerDTO = mapper.toDTO(this)

fun EventBannerDTO.asEntity(mapper: EventBannerMapper): EventBanner = mapper.fromDTO(this)
