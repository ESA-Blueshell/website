package net.blueshell.api.factory.event.web.request

import org.springframework.mock.web.MockMultipartFile
import org.springframework.stereotype.Component

@Component
class EventRequestFactory {
    fun createEventPayload(
        committeeId: Long,
        title: String = "Integration Event ${System.currentTimeMillis()}",
        approved: Boolean = true,
        bannerFileId: Long? = null
    ): String {
        val bannerPart = if (bannerFileId == null) "" else ""","banner":{"fileId":$bannerFileId}"""
        return """{"committeeId":$committeeId,"title":"$title","description":"Event description","location":"Campus","startTime":"2026-03-01T19:00:00Z","endTime":"2026-03-01T21:00:00Z","approved":$approved,"membersOnly":false,"signUp":true$bannerPart}"""
    }

    fun updateEventPayload(
        committeeId: Long,
        version: Long,
        title: String = "Updated Event ${System.currentTimeMillis()}",
        approved: Boolean = false
    ): String =
        """{"committeeId":$committeeId,"title":"$title","description":"Updated description","location":"Updated Campus","startTime":"2026-03-01T19:00:00Z","endTime":"2026-03-01T21:00:00Z","approved":$approved,"membersOnly":false,"signUp":true,"version":$version}"""

    fun eventBannerMultipart(
        filename: String = "banner-it.png",
        contentType: String = "image/png",
        content: ByteArray = "png-content".toByteArray()
    ): MockMultipartFile =
        MockMultipartFile(
            "file",
            filename,
            contentType,
            content
        )
}
