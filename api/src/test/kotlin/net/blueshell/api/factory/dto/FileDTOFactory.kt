package net.blueshell.api.factory.dto

import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.feature.file.dto.FileDTO
import org.springframework.stereotype.Component

/**
 * Factory for FileDTO test instances.
 */
@Component
class FileDTOFactory : BaseDtoFactory<FileDTO>() {

    override fun targetType(): Class<FileDTO> = FileDTO::class.java

    override fun createBasic(): FileDTO {
        val dto = FileDTO()
        dto.name = unique("file")
        dto.mediaType = "image/jpeg"
        dto.size = 1024L
        dto.type = FileType.EVENT_BANNER
        return dto
    }
}
