package net.blueshell.api.auth.domain.twofactor

import net.blueshell.api.auth.domain.CodeLimitReached
import net.blueshell.api.auth.domain.SecurityEvents
import net.blueshell.api.auth.domain.WrongCode
import net.blueshell.api.auth.persistence.SecurityEventKind
import org.springframework.stereotype.Component

/** A code checked against the account's limit of wrong codes, at the challenge and at a step-up alike. */
@Component
class ThrottledCodes(
    private val twoFactor: TwoFactor,
    private val challenges: Challenges,
    private val events: SecurityEvents,
) {
    /** The proof [code] gives; a wrong one counts against the account, and [onWrong] names the tries left. */
    fun prove(
        userId: Long,
        code: String?,
        onWrong: () -> Int? = { null },
    ): Proof {
        if (challenges.isThrottled(userId)) throw CodeLimitReached()
        val proof = code?.takeIf { it.isNotBlank() }?.let { twoFactor.prove(userId, it) }
        if (proof != null) return proof
        val triesLeft = onWrong()
        if (challenges.countFailure(userId)) events.record(userId, SecurityEventKind.CODE_LIMIT_REACHED)
        throw WrongCode(triesLeft)
    }
}
