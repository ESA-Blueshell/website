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
        password: String = "Password123!"
    ): String {
        return """{"username":"$username","initials":"$initials","firstName":"$firstName","lastName":"$lastName","newsletter":$newsletter,"consentPrivacy":$consentPrivacy,"password":"$password","email":"$email","discord":"$discord","phoneNumber":"$phoneNumber"}"""
    }

    fun updateUserPayload(
        discord: String,
        phoneNumber: String,
        version: Long,
        newsletter: Boolean = false
    ): String {
        return """{"kind":"user","discord":"$discord","phoneNumber":"$phoneNumber","newsletter":$newsletter,"version":$version}"""
    }
}
