package net.blueshell.api.domain.user.command

import net.blueshell.api.domain.user.application.query.UserQuery
import net.blueshell.api.domain.user.application.validation.UniqueUserCommand
import net.blueshell.api.domain.user.application.validation.UserUniquenessCandidate
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.Role
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDate

data class UpsertMemberProfileData(
    val dateOfBirth: LocalDate,
    val studentNumber: String,
    val gender: String,
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
    val password: String?,
    override val discord: String,
    override val phoneNumber: String,
    val memberProfile: UpsertMemberProfileData? = null
) : Command<User>, UserUniquenessCandidate {
    override val subjectId: Long? = null
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

data class DeleteUserByIdCommand(
    val userId: Long
) : Command<Unit>

data class ToggleUserRoleCommand(
    val userId: Long,
    val role: Role
) : Command<User>
