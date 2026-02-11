package net.blueshell.api.factory.dto

import net.blueshell.api.domain.file.web.dto.FileDTO
import net.blueshell.api.shared.enums.FileType
import org.springframework.stereotype.Component

/**
 * Factory for FileDTO test instances.
 */
@Component
class FileDTOFactory : BaseDtoFactory<FileDTO>() {

    override fun targetType(): Class<FileDTO> = FileDTO::class.java

    override fun createBasic(): FileDTO {
        return FileDTO(
            name = unique("file"),
            mediaType = "image/jpeg",
            size = 1024L,
            type = FileType.EVENT_BANNER
        )
    }
}
