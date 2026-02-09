package net.blueshell.api.file.web.dto

import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping
import net.blueshell.api.file.persistence.File

@Konverter
interface FileKonverter {
    fun toDTO(file: File): FileDTO

    @Konvert(
        mappings = [
            Mapping(target = "mediaType", ignore = true),
            Mapping(target = "size", ignore = true),
            Mapping(target = "type", ignore = true),
        ]
    )
    fun fromDTO(dto: FileDTO): File
}

private val fileKonverter = Konverter.get<FileKonverter>()

fun FileDTO.asEntity(file: File = File()): File {
    val mapped = fileKonverter.fromDTO(this)
    mapped.mediaType.let { file.mediaType = it }
    mapped.size?.let { file.size = it }
    mapped.type.let { file.type = it }
    id?.let { file.assignIdForRef(it) }
    file.name = mapped.name
    return file
}
