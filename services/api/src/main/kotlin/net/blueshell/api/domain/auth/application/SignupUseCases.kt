package net.blueshell.api.domain.auth.application

import net.blueshell.api.shared.model.SignupSession
import net.blueshell.api.domain.user.application.MemberProfileService
import net.blueshell.api.domain.user.application.SignupDetailsData
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.upsertInto
import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.shared.model.SignupOutcome
import java.time.Instant
import net.blueshell.api.shared.job.TrackedJobDispatcher
import net.blueshell.api.shared.job.EmailJobs
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Everything an unconfirmed signup may do. Each operation is keyed by the signup
 * token rather than an id, so it can only reach the account the token was minted
 * for, and each checks uniqueness imperatively because the account is only known
 * once the token resolves (ADR-024).
 */
@Service
class SignupUseCases(
    private val signupTokens: SignupTokenService,
    private val users: UserService,
    private val memberProfiles: MemberProfileService,
    private val completion: SignupCompletionService,
    private val activation: UserActivationService,
    private val jobs: TrackedJobDispatcher,
) {
    fun issueSession(userId: Long): SignupSession = signupTokens.issue(users.findById(userId))

    @Transactional
    fun correctEmail(signupToken: String, email: String) {
        val account = signupTokens.resolveAccount(signupToken)
        if (account.user.enabled) {
            throw AccessDeniedException("A confirmed email address is changed through account settings")
        }
        // Checked here rather than by a declarative validator because the account is
        // only known once the token resolves — the precedent set in ADR-024.
        if (users.existsByEmailAndIdNot(email, account.id)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "That email address is already in use")
        }
        account.user.email = email
        users.update(account.user)
        // requestUserActivation retires whatever was outstanding before issuing the
        // replacement. That matters most here: the mistyped address may be somebody
        // else's inbox, so a link already delivered there has to stop working.
        val dispatch = requireNotNull(activation.requestUserActivation(account.user.username)) {
            "Expected an activation dispatch for the unconfirmed account ${account.id}"
        }
        jobs.runAsync(
            EmailJobs.Recovery,
            EmailJobs.RecoveryPayload(dispatch.userId, dispatch.rawToken, dispatch.type),
        )
    }

    /**
     * Correcting what was typed at the first signup step. The email address is
     * absent on purpose: changing it invalidates the confirmation link, so it
     * travels through [correctEmail].
     */
    @Transactional
    fun updateDetails(signupToken: String, data: SignupDetailsData) {
        val account = signupTokens.resolveAccount(signupToken)
        if (account.user.enabled) {
            throw AccessDeniedException("A confirmed account changes its details under a session")
        }

        refuseIfTaken(users.existsByUsernameAndIdNot(data.username, account.id), "That username is already in use")
        refuseIfTaken(users.existsByDiscordAndIdNot(data.discord, account.id), "That Discord name is already in use")
        refuseIfTaken(
            users.existsByPhoneNumberAndIdNot(data.phoneNumber, account.id),
            "That phone number is already in use"
        )

        users.update(
            account.user.apply {
                username = data.username
                initials = data.initials
                firstName = data.firstName
                prefix = data.prefix
                lastName = data.lastName
                discord = data.discord
                phoneNumber = data.phoneNumber
                newsletter = data.newsletter
                photoConsent = data.photoConsent
                data.memberProfile?.upsertInto(this)
            }
        )
    }

    // Transactional so the account resolved from the token stays managed: without
    // it the read closes its own transaction and the entity comes back detached.
    @Transactional
    fun saveAddress(
        signupToken: String,
        country: String,
        city: String,
        street: String,
        houseNumber: String,
        zipCode: String,
    ) {
        val user = signupTokens.resolveAccount(signupToken).user
        // replaceAddress is an upsert, so going back a step and correcting the
        // address works without the client tracking an id.
        user.replaceAddress(
            Address(
                user = user,
                country = country,
                city = city,
                street = street,
                houseNumber = houseNumber,
                zipCode = zipCode,
            )
        )
        users.update(user)
    }

    /**
     * Unlike the signed-in route, a non-commit here is the normal case: the email
     * address may not be confirmed yet.
     */
    @Transactional
    fun submitApplication(signupToken: String): SignupOutcome {
        val account = signupTokens.resolveAccount(signupToken)
        val profile = account.user.memberProfile
            ?: throw AccessDeniedException("This signup did not apply for membership")
        profile.conditionsAcceptedAt = Instant.now()
        memberProfiles.update(profile)

        return completion.completeIfReady(account.id)
    }

    private fun refuseIfTaken(taken: Boolean, message: String) {
        if (taken) throw ResponseStatusException(HttpStatus.CONFLICT, message)
    }
}
