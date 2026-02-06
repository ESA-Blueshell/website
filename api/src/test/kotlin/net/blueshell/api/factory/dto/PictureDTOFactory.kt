package net.blueshell.api.factory.dto

import net.blueshell.api.dto.PictureDTO
import org.springframework.stereotype.Component

/**
 * Factory for PictureDTO test instances.
 */
@Component
class PictureDTOFactory : BaseDtoFactory<PictureDTO>() {

    override fun targetType(): Class<PictureDTO> = PictureDTO::class.java

    override fun createBasic(): PictureDTO {
        val dto = PictureDTO()
        dto.name = unique("picture")
        dto.url = "https://cdn.example.com/${unique("pic")}.jpg"
        dto.uploaderId = nextId()
        dto.eventId = nextId()
        return dto
    }
}
