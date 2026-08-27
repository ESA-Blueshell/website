package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.auth.application.SignupCompletionService
import net.blueshell.api.domain.auth.application.SignupTokenService
import net.blueshell.api.domain.user.application.MemberProfileService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.SaveSignupAddressCommand
import net.blueshell.api.domain.user.command.SubmitSignupApplicationCommand
import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.model.SignupOutcome
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class SaveSignupAddressHandler(
    private val users: UserService,
    private val signupTokens: SignupTokenService
) : CommandHandler<SaveSignupAddressCommand, Unit> {
    override val commandType = SaveSignupAddressCommand::class

    // Transactional so the account resolved from the token stays managed: without
    // it the read closes its own transaction and the entity comes back detached.
    @Transactional
    override fun handle(command: SaveSignupAddressCommand) {
        val user = signupTokens.resolveAccount(command.signupToken).user
        // replaceAddress is an upsert, so going back a step and correcting the
        // address works without the client tracking an id.
        user.replaceAddress(
            Address(
                user = user,
                country = command.country,
                city = command.city,
                street = command.street,
                houseNumber = command.houseNumber,
                zipCode = command.zipCode,
            )
        )
        users.update(user)
    }
}

@Component
class SubmitSignupApplicationHandler(
    private val memberProfiles: MemberProfileService,
    private val signupTokens: SignupTokenService,
    private val completion: SignupCompletionService
) : CommandHandler<SubmitSignupApplicationCommand, SignupOutcome> {
    override val commandType = SubmitSignupApplicationCommand::class

    @Transactional
    override fun handle(command: SubmitSignupApplicationCommand): SignupOutcome {
        val account = signupTokens.resolveAccount(command.signupToken)
        val profile = account.user.memberProfile
            ?: throw AccessDeniedException("This signup did not apply for membership")
        profile.conditionsAcceptedAt = Instant.now()
        memberProfiles.update(profile)

        // Unlike the signed-in route, a non-commit here is the normal case: the
        // email address may not be confirmed yet.
        return completion.completeIfReady(account.id)
    }
}
