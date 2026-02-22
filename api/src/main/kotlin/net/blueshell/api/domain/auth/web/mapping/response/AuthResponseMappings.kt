package net.blueshell.api.domain.auth.web.mapping.response

import net.blueshell.api.domain.auth.domain.model.AuthenticationSession
import net.blueshell.api.domain.auth.web.dto.response.AuthenticationResponse

fun AuthenticationSession.asResponse(): AuthenticationResponse {
    return AuthenticationResponse(
        token = token,
        userId = userId,
        username = username,
        expiration = expiresAtEpochMs,
        roles = roles.toMutableSet(),
        addressId = addressId
    )
}
