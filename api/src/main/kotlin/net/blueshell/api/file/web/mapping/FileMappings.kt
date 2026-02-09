package net.blueshell.api.file.web.mapping

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.web.dto.FileDTO

@Konverter
interface FileKonverter {
    fun toDTO(file: File): FileDTO

    fun fromDTO(dto: FileDTO): File
}

private val fileKonverter = Konverter.get<FileKonverter>()

fun FileDTO.asEntity(): File {
    val mapped = fileKonverter.fromDTO(this)
    id?.let { mapped.assignIdForRef(it) }
    return mapped
}

fun File.asDto(): FileDTO = fileKonverter.toDTO(this)
