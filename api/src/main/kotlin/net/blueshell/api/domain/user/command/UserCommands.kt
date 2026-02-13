package net.blueshell.api.domain.user.command

import net.blueshell.api.domain.user.application.query.UserQuery
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.validation.UniqueUserCommand
import net.blueshell.api.shared.validation.UserUniquenessCandidate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.sql.Date

@UniqueUserCommand
data class CreateUserCommand(
    val isBoard: Boolean,
    val roles: Set<Role>,
    val dateOfBirth: Date?,
    val nationality: String?,
    val photoConsent: Boolean,
    val ehbo: Boolean,
    val bhv: Boolean,
    val enabled: Boolean,
    val gender: String?,
    val studentNumber: String?,
    override val username: String?,
    override val email: String?,
    val initials: String?,
    val firstName: String?,
    val prefix: String?,
    val lastName: String?,
    val newsletter: Boolean,
    val password: String?,
    val addressId: Long?,
    override val discord: String?,
    override val phoneNumber: String?
) : Command<User>, UserUniquenessCandidate {
    override val subjectId: Long? = null
}

@UniqueUserCommand
data class CreateGuestUserCommand(
    override val username: String?,
    val initials: String?,
    val firstName: String?,
    val prefix: String?,
    val lastName: String?,
    val newsletter: Boolean,
    val password: String?,
    val addressId: Long?,
    override val email: String?,
    override val discord: String?,
    override val phoneNumber: String?
) : Command<User>, UserUniquenessCandidate {
    override val subjectId: Long? = null
}

@UniqueUserCommand
data class UpdateGuestUserCommand(
    val id: Long,
    override val discord: String?,
    override val phoneNumber: String?,
    val newsletter: Boolean,
    val version: Long?
) : Command<User>, UserUniquenessCandidate {
    override val subjectId: Long? = id
    override val username: String? = null
    override val email: String? = null
}

@UniqueUserCommand
data class UpdateUserCommand(
    val id: Long,
    val isBoard: Boolean,
    val roles: Set<Role>,
    val dateOfBirth: Date?,
    val nationality: String?,
    val photoConsent: Boolean,
    val ehbo: Boolean,
    val bhv: Boolean,
    val enabled: Boolean,
    val gender: String?,
    val studentNumber: String?,
    override val username: String?,
    override val email: String?,
    val initials: String?,
    val firstName: String?,
    val prefix: String?,
    val lastName: String?,
    val newsletter: Boolean,
    val addressId: Long?,
    override val discord: String?,
    override val phoneNumber: String?,
    val version: Long?
) : Command<User>, UserUniquenessCandidate {
    override val subjectId: Long = id
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
