package net.blueshell.api.auth.email

import net.blueshell.api.platform.integration.email.model.base.BaseEmail
import net.blueshell.api.user.model.User

abstract class RecoveryEmail(
    recipient: User,
    protected val token: String,
    frontendUrl: String,
    appUrl: String
) : BaseEmail(recipient, frontendUrl, appUrl)
