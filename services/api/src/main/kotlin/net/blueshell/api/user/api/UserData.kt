package net.blueshell.api.user.api

import java.sql.Date

/**
 * The editable shapes of a user, as the application layer accepts them. Public
 * registration, a board edit and a self-service edit differ in which fields they
 * are allowed to set, so each is declared separately rather than sharing one
 * nullable superset. They lived on three near-identical commands before.
 */
data class NewUserData(
    val username: String,
    val email: String,
    val initials: String,
    val firstName: String,
    val prefix: String?,
    val lastName: String,
    val newsletter: Boolean,
    val consentPrivacy: Boolean,
    val photoConsent: Boolean,
    val password: String?,
    val discord: String,
    val phoneNumber: String,
    val memberProfile: UpsertMemberProfileData? = null,
)

data class BoardUserData(
    val username: String,
    val email: String,
    val initials: String,
    val firstName: String,
    val prefix: String?,
    val lastName: String,
    val newsletter: Boolean,
    val photoConsent: Boolean,
    val discord: String,
    val phoneNumber: String,
    val version: Long,
    val memberProfile: UpsertMemberProfileData? = null,
)

data class SelfUserData(
    val discord: String,
    val phoneNumber: String,
    val newsletter: Boolean,
    val photoConsent: Boolean,
    val version: Long,
    val memberProfile: UpsertMemberProfileData? = null,
)

/** What the signup routes may correct while the account is still unconfirmed. */
data class SignupDetailsData(
    val username: String,
    val initials: String,
    val firstName: String,
    val prefix: String?,
    val lastName: String,
    val discord: String,
    val phoneNumber: String,
    val newsletter: Boolean,
    val photoConsent: Boolean,
    val memberProfile: UpsertMemberProfileData? = null,
)

data class UpsertMemberProfileData(
    val dateOfBirth: Date,
    val studentNumber: String?,
    val gender: String?,
    val nationality: String,
    val bhv: Boolean,
    val ehbo: Boolean,
    val nameOnTeamPages: Boolean = false,
    val version: Long? = null,
)
