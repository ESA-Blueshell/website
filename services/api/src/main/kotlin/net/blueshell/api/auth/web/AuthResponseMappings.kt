package net.blueshell.api.auth.web

import net.blueshell.api.auth.domain.Signer

fun Signer.asResponse(): AuthenticationResponse = AuthenticationResponse(userId, username, roles, addressId)
