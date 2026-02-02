package net.blueshell.api.email.recovery

import lombok.Getter
import net.blueshell.api.base.BaseEmail
import net.blueshell.api.model.User

abstract class RecoveryEmail(
    recipient: User?,
    @field:Getter protected val token: String?,
    frontendUrl: String?,
    appUrl: String?
) : BaseEmail(recipient, frontendUrl, appUrl)
