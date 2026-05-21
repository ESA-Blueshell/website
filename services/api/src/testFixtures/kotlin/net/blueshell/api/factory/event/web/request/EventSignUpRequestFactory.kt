package net.blueshell.api.factory.event.web.request

import org.springframework.stereotype.Component

@Component
class EventSignUpRequestFactory {
    fun createUserSignUpPayload(userId: Long): String =
        """{"userId":$userId}"""

    fun updateUserSignUpPayload(userId: Long, version: Long): String =
        """{"userId":$userId,"version":$version}"""

    fun createGuestSignUpPayload(
        name: String = "Guest User",
        discord: String = "guest#1234",
        email: String = "guest@example.com",
        phoneNumber: String = "+31612345678"
    ): String =
        """{"guest":{"name":"$name","discord":"$discord","email":"$email","phoneNumber":"$phoneNumber"}}"""

    fun updateGuestSignUpPayload(
        version: Long,
        name: String = "Guest Updated",
        discord: String = "guest#1234",
        email: String = "guest@example.com",
        phoneNumber: String = "+31612345678"
    ): String =
        """{"guest":{"name":"$name","discord":"$discord","email":"$email","phoneNumber":"$phoneNumber"},"version":$version}"""

    fun createUserSignUpPayload(userId: Long, answersJson: String): String =
        """{"userId":$userId,"answers":$answersJson}"""

    fun updateUserSignUpPayload(userId: Long, version: Long, answersJson: String): String =
        """{"userId":$userId,"version":$version,"answers":$answersJson}"""

    fun openAnswerJson(questionId: Long, text: String?): String {
        val textPart = text?.let { ""","textResponse":${escapeJson(it)}""" } ?: ""
        return """{"questionId":$questionId$textPart}"""
    }

    fun selectionsAnswerJson(questionId: Long, selections: List<Boolean>): String =
        """{"questionId":$questionId,"optionSelections":[${selections.joinToString(",")}]}"""

    fun answersArray(vararg answersJson: String): String =
        "[${answersJson.joinToString(",")}]"

    private fun escapeJson(s: String): String {
        val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
