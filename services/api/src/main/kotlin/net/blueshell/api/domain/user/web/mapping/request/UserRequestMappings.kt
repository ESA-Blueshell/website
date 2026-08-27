package net.blueshell.api.domain.user.web.mapping.request

import net.blueshell.api.domain.user.application.BoardUserData
import net.blueshell.api.domain.user.application.NewUserData
import net.blueshell.api.domain.user.application.SelfUserData
import net.blueshell.api.domain.user.web.dto.request.BoardUpdateUserRequest
import net.blueshell.api.domain.user.web.dto.request.CreateUserRequest
import net.blueshell.api.domain.user.web.dto.request.UpdateUserRequest

fun CreateUserRequest.asData(): NewUserData =
    NewUserData(
        username = this.username,
        initials = this.initials,
        firstName = this.firstName,
        prefix = this.prefix,
        lastName = this.lastName,
        newsletter = this.newsletter,
        consentPrivacy = this.consentPrivacy ?: false,
        photoConsent = this.photoConsent ?: false,
        password = this.password,
        email = this.email,
        discord = this.discord,
        phoneNumber = this.phoneNumber,
        memberProfile = this.memberProfile?.asCommandData()
    )

// Named distinctly from `UpdateUserRequest.asData` (its supertype) so the two
// mappings are not confusable overloads: `BoardUpdateUserRequest` extends
// `UpdateUserRequest`, and identical parameter lists on receivers in a subtype
// relationship trip CodeQL's java/confusing-method-signature.
fun BoardUpdateUserRequest.asBoardData(): BoardUserData =
    BoardUserData(
        username = this.username,
        initials = this.initials,
        firstName = this.firstName,
        prefix = this.prefix,
        lastName = this.lastName,
        newsletter = this.newsletter,
        photoConsent = this.photoConsent ?: false,
        email = this.email,
        discord = this.discord,
        phoneNumber = this.phoneNumber,
        version = this.version,
        memberProfile = this.memberProfile?.asCommandData()
    )

fun UpdateUserRequest.asData(): SelfUserData =
    SelfUserData(
        discord = this.discord,
        phoneNumber = this.phoneNumber,
        newsletter = this.newsletter,
        photoConsent = this.photoConsent ?: false,
        version = this.version,
        memberProfile = this.memberProfile?.asCommandData()
    )
