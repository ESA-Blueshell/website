package net.blueshell.api.domain.file.application.command

import net.blueshell.api.domain.file.application.FileService
import net.blueshell.api.domain.file.command.DownloadEventBannerCommand
import net.blueshell.api.domain.file.command.UploadEventBannerCommand
import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.enums.FileType
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

@Component
class DownloadEventBannerHandler(
    private val service: FileService
) : CommandHandler<DownloadEventBannerCommand, ResponseEntity<Resource>> {
    override val commandType = DownloadEventBannerCommand::class

    override fun handle(command: DownloadEventBannerCommand): ResponseEntity<Resource> {
        val file = service.findByBannerEventId(command.eventId)
        return service.prepareFileResponse(file)
    }
}

@Component
class UploadEventBannerHandler(
    private val service: FileService
) : CommandHandler<UploadEventBannerCommand, File> {
    override val commandType = UploadEventBannerCommand::class

    override fun handle(command: UploadEventBannerCommand): File {
        return service.storeMultipart(command.file, FileType.EVENT_BANNER)
    }
}
