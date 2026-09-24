package net.blueshell.api.factory.user.web.request

import org.springframework.stereotype.Component

@Component
class UserRequestFactory {
    fun createUserPayload(
        username: String,
        email: String,
        discord: String = "guest#1234",
        phoneNumber: String = "+31612345678",
        initials: String = "GU",
        firstName: String = "Guest",
        lastName: String = "User",
        newsletter: Boolean = true,
        consentPrivacy: Boolean = true,
        password: String = "Password123!",
        discordId: String? = null,
    ): String {
        val linked = discordId?.let { ""","discordId":"$it"""" }.orEmpty()
        return """
            {"username":"$username","initials":"$initials","firstName":"$firstName","lastName":"$lastName","newsletter":$newsletter,"consentPrivacy":$consentPrivacy,"password":"$password","email":"$email","discord":"$discord"$linked,"phoneNumber":"$phoneNumber"}
            """.trimIndent()
    }

    fun updateUserPayload(
        discord: String,
        phoneNumber: String,
        version: Long,
        newsletter: Boolean = false,
        discordId: String? = null,
    ): String {
        val linked = discordId?.let { ""","discordId":"$it"""" }.orEmpty()
        return """{"kind":"user","discord":"$discord"$linked,"phoneNumber":"$phoneNumber","newsletter":$newsletter,"version":$version}"""
    }
}
