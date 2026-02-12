package net.blueshell.api.domain.auth.application.email

import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.email.model.base.BaseEmail

abstract class RecoveryEmail(
    recipient: User,
    protected val token: String,
    frontendUrl: String,
    appUrl: String
) : BaseEmail(recipient, frontendUrl, appUrl)
