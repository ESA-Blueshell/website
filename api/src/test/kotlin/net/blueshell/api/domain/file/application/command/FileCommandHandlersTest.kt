package net.blueshell.api.domain.file.application.command

import net.blueshell.api.domain.file.application.FileService
import net.blueshell.api.domain.file.command.DownloadEventBannerCommand
import net.blueshell.api.domain.file.command.UploadEventBannerCommand
import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.multipart.MultipartFile

class FileCommandHandlersTest {

    private val fileService = mock<FileService>()

    @Nested
    inner class DownloadEventBanner {

        private val handler = DownloadEventBannerHandler(fileService)

        @Test
        fun `loads banner file and returns prepared response`() {
            val file = mock<File>()
            val resource: Resource = ByteArrayResource("banner".toByteArray())
            val response: ResponseEntity<Resource> = ResponseEntity.ok(resource)
            whenever(fileService.findByBannerEventId(14L)).thenReturn(file)
            whenever(fileService.prepareFileResponse(file)).thenReturn(response)

            val result = handler.handle(DownloadEventBannerCommand(eventId = 14L))

            assertThat(result).isSameAs(response)
            verify(fileService).findByBannerEventId(eq(14L))
            verify(fileService).prepareFileResponse(eq(file))
        }
    }

    @Nested
    inner class UploadEventBanner {

        private val handler = UploadEventBannerHandler(fileService)

        @Test
        fun `stores multipart file as event banner`() {
            val multipart = mock<MultipartFile>()
            val stored = mock<File>()
            whenever(fileService.storeMultipart(multipart, FileType.EVENT_BANNER)).thenReturn(stored)

            val result = handler.handle(UploadEventBannerCommand(file = multipart))

            assertThat(result).isSameAs(stored)
            verify(fileService).storeMultipart(eq(multipart), eq(FileType.EVENT_BANNER))
        }
    }
}
