package net.blueshell.api.factory.auth.web.request

import org.springframework.stereotype.Component

@Component
class AuthRequestFactory {
    fun authenticatePayload(username: String, password: String): String =
        """{"username":"$username","password":"$password"}"""

    fun passwordResetPayload(token: String, password: String): String =
        """{"token":"$token","password":"$password"}"""

    fun userActivationPayload(token: String): String =
        """{"token":"$token"}"""

    fun memberActivationPayload(token: String, username: String, password: String): String =
        """{"token":"$token","username":"$username","password":"$password"}"""
}
