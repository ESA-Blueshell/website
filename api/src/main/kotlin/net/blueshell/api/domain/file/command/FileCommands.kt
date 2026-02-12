package net.blueshell.api.domain.file.command

import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.shared.command.Command
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.multipart.MultipartFile

data class DownloadEventBannerCommand(
    val eventId: Long
) : Command<ResponseEntity<Resource>>

data class UploadEventBannerCommand(
    val file: MultipartFile
) : Command<File>
