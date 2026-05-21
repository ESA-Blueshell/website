package net.blueshell.api.factory.event.web.request

import org.springframework.mock.web.MockMultipartFile
import org.springframework.stereotype.Component

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
