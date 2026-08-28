package net.blueshell.api.auth.domain

import net.blueshell.api.user.api.SignupCompletion
import net.blueshell.api.shared.model.SignupOutcome
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.Membership
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * The single place a membership is created (ADR-025).
 *
 * Two facts make somebody a member and they arrive independently: the email
 * address is confirmed, and the application is submitted. Callers invoke this
 * after any write that could have supplied the last missing one, and whichever
 * write completes the set is the one that commits. Every precondition is re-read
 * from the database rather than taken from a token or a principal, so a cached
 * principal minted before the address existed cannot admit a membership.
 */
@Service
class SignupCompletionService(
    private val users: UserService,
    private val memberships: MembershipService,
    private val signupTokens: SignupTokenService,
) : SignupCompletion {

    @Transactional
    override fun completeIfReady(userId: Long): SignupOutcome {
        val user = users.findById(userId)

        if (memberships.existsActiveMembershipByUserId(userId)) {
            return SignupOutcome(emailConfirmed = user.enabled, membershipStarted = false)
        }
        if (!user.enabled) {
            return SignupOutcome(emailConfirmed = false, membershipStarted = false)
        }

        val profile = user.memberProfile
            ?: return SignupOutcome(emailConfirmed = true, membershipStarted = false)
        if (profile.conditionsAcceptedAt == null) {
            return SignupOutcome(emailConfirmed = true, membershipStarted = false)
        }
        if (user.addressId == null) {
            return SignupOutcome(emailConfirmed = true, membershipStarted = false)
        }

        memberships.create(Membership(user = user, startDate = LocalDate.now()))
        signupTokens.retire(userId)
        return SignupOutcome(emailConfirmed = true, membershipStarted = true)
    }
}
