package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** What an operator may do when no admin can: the last admin lost both factors, or is locked. */
enum class BreakGlassAction { UNLOCK, RESET_TWO_FACTOR }

/**
 * The operator's way in (`docs/runbooks/break-glass.md`): the same unlock and two-factor reset an
 * admin performs, recorded with the operator as actor, and told to every admin.
 */
@Service
class BreakGlass(
    private val users: UserService,
    private val accountSecurity: AccountSecurity,
    private val recovery: RecoveryUseCases,
    private val events: SecurityEvents,
) {
    @Transactional
    fun run(
        action: BreakGlassAction,
        username: String,
        reason: String,
    ) {
        require(reason.isNotBlank()) { "A break-glass run needs a reason" }
        val user = users.findByUsername(username)
        val userId = requireNotNull(user.id)
        when (action) {
            BreakGlassAction.UNLOCK -> {
                user.lockedAt = null
                users.update(user)
                events.record(userId, SecurityEventKind.ACCOUNT_UNLOCKED, SecurityActor.Operator, note = reason, browser = null)
                recovery.resetPassword(user.username)
            }
            BreakGlassAction.RESET_TWO_FACTOR -> accountSecurity.resetTwoFactor(userId, SecurityActor.Operator, reason)
        }
        events.record(userId, SecurityEventKind.BREAK_GLASS, SecurityActor.Operator, note = "$action: $reason", browser = null)
    }
}
