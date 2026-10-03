package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.LastRecoveryEmail
import net.blueshell.api.auth.persistence.RecoveryTokenRepository
import net.blueshell.api.shared.enums.TokenPurpose
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** When each account was last sent an email that helps it in: an activation or a password reset. */
@Service
class LastRecoveryEmails(
    private val tokens: RecoveryTokenRepository,
) {
    @Transactional(readOnly = true)
    fun all(): List<LastRecoveryEmail> = tokens.findLastIssuedPerUser(HELPING_IN)

    companion object {
        val HELPING_IN = setOf(TokenPurpose.USER_ACTIVATION, TokenPurpose.MEMBER_ACTIVATION, TokenPurpose.PASSWORD_RESET)
    }
}
