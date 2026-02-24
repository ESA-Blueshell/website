package net.blueshell.api.domain.user.command

import jakarta.validation.constraints.AssertTrue
import net.blueshell.api.domain.user.application.query.UserQuery
import net.blueshell.api.domain.user.application.validation.UniqueUserCommand
import net.blueshell.api.domain.user.application.validation.UserUniquenessCandidate
import net.blueshell.api.domain.user.persistence.DeletedUser
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.Role
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.sql.Date

data class UpsertMemberProfileData(
    val dateOfBirth: Date,
    val studentNumber: String,
    val gender: String?,
    val photoConsent: Boolean,
    val nationality: String,
    val bhv: Boolean,
    val ehbo: Boolean,
    val version: Long? = null
)

@UniqueUserCommand
data class CreateUserCommand(
    val isBoard: Boolean,
    override val username: String,
    override val email: String,
    val initials: String,
    val firstName: String,
    val prefix: String?,
    val lastName: String,
    val newsletter: Boolean,
    val consentPrivacy: Boolean,
    val password: String?,
    override val discord: String,
    override val phoneNumber: String,
    val memberProfile: UpsertMemberProfileData? = null
) : Command<User>, UserUniquenessCandidate {
    override val subjectId: Long? = null

    @get:AssertTrue(message = "Password is required for public user registration.")
    val isPasswordPresentForPublicRegistration: Boolean
        get() = isBoard || !password.isNullOrBlank()

    @get:AssertTrue(message = "Privacy policy consent is required for public user registration.")
    val isPrivacyConsentGivenForPublicRegistration: Boolean
        get() = isBoard || consentPrivacy

    @get:AssertTrue(
        message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)."
    )
    val isPasswordComplexForPublicRegistration: Boolean
        get() = isBoard || (password?.let(PASSWORD_COMPLEXITY_REGEX::matches) == true)

    companion object {
        private val PASSWORD_COMPLEXITY_REGEX =
            Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$")
    }
}

@UniqueUserCommand
data class BoardUpdateUserCommand(
    val id: Long,
    override val username: String,
    override val email: String,
    val initials: String,
    val firstName: String,
    val prefix: String?,
    val lastName: String,
    val newsletter: Boolean,
    override val discord: String,
    override val phoneNumber: String,
    val version: Long,
    val memberProfile: UpsertMemberProfileData? = null
) : Command<User>, UserUniquenessCandidate {
    override val subjectId: Long = id
}

@UniqueUserCommand
data class UpdateUserCommand(
    var id: Long,
    override val discord: String,
    override val phoneNumber: String,
    val newsletter: Boolean,
    val version: Long,
    val memberProfile: UpsertMemberProfileData? = null
) : Command<User>, UserUniquenessCandidate {
    override val subjectId: Long = id
    override val username: String? = null
    override val email: String? = null
}

data class FindUsersCommand(
    val filter: UserQuery,
    val pageable: Pageable
) : Command<Page<User>>

data class FindUserByIdCommand(
    val userId: Long
) : Command<User>

data class FindDeletedUsersCommand(
    val pageable: Pageable
) : Command<Page<DeletedUser>>

data class DeleteUserByIdCommand(
    val userId: Long
) : Command<Unit>

data class RestoreDeletedUserByIdCommand(
    val userId: Long
) : Command<Unit>

data class ToggleUserRoleCommand(
    val userId: Long,
    val role: Role
) : Command<User>
