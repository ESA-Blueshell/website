package net.blueshell.api.auth.web

import net.blueshell.api.auth.domain.Signer
import net.blueshell.api.auth.domain.twofactor.TwoFactorStanding

fun Signer.asResponse(): AuthenticationResponse = AuthenticationResponse(userId, username, roles, addressId, twoFactor.asResponse())

fun TwoFactorStanding.asResponse(): TwoFactorStandingResponse = TwoFactorStandingResponse(
    on,
    backupCodesLeft,
    required,
    offered,
    mayTurnOff,
)
