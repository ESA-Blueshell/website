package net.blueshell.api.factory.event.web.request

import org.springframework.mock.web.MockMultipartFile
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Component
class EventRequestFactory {
    fun createEventPayload(
        committeeId: Long,
        title: String = "Integration Event ${System.currentTimeMillis()}",
        approved: Boolean = true,
        bannerFileId: Long? = null,
        signUpFormJson: String? = null,
        signUpDeadline: String? = null,
        signUpLimit: Int? = null,
        startTime: String = "2026-03-01T19:00:00Z",
        endTime: String = "2026-03-01T21:00:00Z"
    ): String {
        val bannerPart = if (bannerFileId == null) "" else ""","banner":{"fileId":$bannerFileId}"""
        val signUpFormPart = signUpFormJson?.let { ""","signUpForm":$it""" } ?: ""
        val deadlinePart = signUpDeadline?.let { ""","signUpDeadline":"$it"""" } ?: ""
        val limitPart = signUpLimit?.let { ""","signUpLimit":$it""" } ?: ""
        return """{"committeeId":$committeeId,"title":"$title","description":"Event description","location":"Campus","startTime":"$startTime","endTime":"$endTime","approved":$approved,"membersOnly":false,"signUp":true$bannerPart$signUpFormPart$deadlinePart$limitPart}"""
    }

    fun updateEventPayload(
        committeeId: Long,
        version: Long,
        title: String = "Updated Event ${System.currentTimeMillis()}",
        approved: Boolean = false,
        bannerFileId: Long? = null,
        signUpFormJson: String? = null,
        signUpDeadline: String? = null,
        signUpLimit: Int? = null,
        removeExistingSignUps: Boolean = false,
        startTime: String = "2026-03-01T19:00:00Z",
        endTime: String = "2026-03-01T21:00:00Z"
    ): String {
        val bannerPart = if (bannerFileId == null) "" else ""","banner":{"fileId":$bannerFileId}"""
        val signUpFormPart = signUpFormJson?.let { ""","signUpForm":$it""" } ?: ""
        val deadlinePart = signUpDeadline?.let { ""","signUpDeadline":"$it"""" } ?: ""
        val limitPart = signUpLimit?.let { ""","signUpLimit":$it""" } ?: ""
        val removePart = ""","removeExistingSignUps":$removeExistingSignUps"""
        return """{"committeeId":$committeeId,"title":"$title","description":"Updated description","location":"Updated Campus","startTime":"$startTime","endTime":"$endTime","approved":$approved,"membersOnly":false,"signUp":true$bannerPart$signUpFormPart$deadlinePart$limitPart$removePart,"version":$version}"""
    }

    fun questionJson(idx: Long, type: String, label: String, required: Boolean? = null): String {
        val requiredPart = required?.let { ""","required":$it""" } ?: ""
        return """{"idx":$idx,"type":"$type","label":"$label"$requiredPart}"""
    }

    fun signUpFormJson(vararg questionJson: String): String =
        """{"questions":[${questionJson.joinToString(",")}]}"""

    /**
     * A banner upload, and a real picture by default.
     *
     * An event banner is converted and stored at several widths, so bytes that only claim to
     * be a picture are refused. A caller wanting that refusal passes its own [content].
     */
    fun eventBannerMultipart(
        filename: String = "banner-it.png",
        contentType: String = "image/png",
        content: ByteArray = pngBytes()
    ): MockMultipartFile =
        MockMultipartFile(
            "file",
            filename,
            contentType,
            content
        )

    /** A real picture of the asked size, for the kinds that are converted on the way in. */
    fun pngBytes(width: Int = 1600, height: Int = 900): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
}
