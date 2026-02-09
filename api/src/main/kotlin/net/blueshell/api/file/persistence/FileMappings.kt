package net.blueshell.api.file.persistence

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.file.web.dto.FileDTO
import net.blueshell.api.file.web.dto.FileKonverter

private val fileKonverter = Konverter.get<FileKonverter>()

fun File.asDto(): FileDTO = fileKonverter.toDTO(this)
