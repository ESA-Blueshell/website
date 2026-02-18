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
}
