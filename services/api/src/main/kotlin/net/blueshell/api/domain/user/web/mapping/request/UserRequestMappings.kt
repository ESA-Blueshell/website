package net.blueshell.api.domain.user.web.mapping.request

import net.blueshell.api.domain.user.command.BoardUpdateUserCommand
import net.blueshell.api.domain.user.command.CreateUserCommand
import net.blueshell.api.domain.user.command.UpdateUserCommand
import net.blueshell.api.domain.user.web.dto.request.BoardUpdateUserRequest
import net.blueshell.api.domain.user.web.dto.request.CreateUserRequest
import net.blueshell.api.domain.user.web.dto.request.UpdateUserRequest

fun CreateUserRequest.asCommand(isBoard: Boolean): CreateUserCommand =
    CreateUserCommand(
        isBoard = isBoard,
        username = this.username!!,
        initials = this.initials!!,
        firstName = this.firstName!!,
        prefix = this.prefix,
        lastName = this.lastName!!,
        newsletter = this.newsletter!!,
        consentPrivacy = this.consentPrivacy ?: false,
        photoConsent = this.photoConsent ?: false,
        password = this.password,
        email = this.email!!,
        discord = this.discord!!,
        phoneNumber = this.phoneNumber!!,
        memberProfile = this.memberProfile?.asCommandData()
    )

// Named distinctly from `UpdateUserRequest.asCommand` (its supertype) so the two
// mappings are not confusable overloads: `BoardUpdateUserRequest` extends
// `UpdateUserRequest`, and identical parameter lists on receivers in a subtype
// relationship trip CodeQL's java/confusing-method-signature.
fun BoardUpdateUserRequest.asBoardCommand(id: Long): BoardUpdateUserCommand =
    BoardUpdateUserCommand(
        id = id,
        username = this.username!!,
        initials = this.initials!!,
        firstName = this.firstName!!,
        prefix = this.prefix,
        lastName = this.lastName!!,
        newsletter = this.newsletter!!,
        photoConsent = this.photoConsent ?: false,
        email = this.email!!,
        discord = this.discord!!,
        phoneNumber = this.phoneNumber!!,
        version = this.version!!,
        memberProfile = this.memberProfile?.asCommandData()
    )

fun UpdateUserRequest.asCommand(id: Long): UpdateUserCommand =
    UpdateUserCommand(
        id = id,
        discord = this.discord!!,
        phoneNumber = this.phoneNumber!!,
        newsletter = this.newsletter!!,
        photoConsent = this.photoConsent ?: false,
        version = this.version!!,
        memberProfile = this.memberProfile?.asCommandData()
    )
