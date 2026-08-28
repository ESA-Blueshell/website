package net.blueshell.api.auth.web

import net.blueshell.api.auth.domain.AuthenticationSession

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
