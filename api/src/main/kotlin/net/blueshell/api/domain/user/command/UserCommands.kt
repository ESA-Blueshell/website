package net.blueshell.api.domain.user.command

import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.persistence.filter.UserFilter
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.Role
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.sql.Date

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
    val username: String?,
    val email: String?,
    val initials: String?,
    val firstName: String?,
    val prefix: String?,
    val lastName: String?,
    val newsletter: Boolean,
    val password: String?,
    val addressId: Long?,
    val discord: String?,
    val phoneNumber: String?
) : Command<User>

data class CreateGuestUserCommand(
    val username: String?,
    val initials: String?,
    val firstName: String?,
    val prefix: String?,
    val lastName: String?,
    val newsletter: Boolean,
    val password: String?,
    val addressId: Long?,
    val email: String?,
    val discord: String?,
    val phoneNumber: String?
) : Command<User>

data class UpdateGuestUserCommand(
    val id: Long,
    val discord: String?,
    val phoneNumber: String?,
    val newsletter: Boolean,
    val version: Long?
) : Command<User>

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
    val username: String?,
    val email: String?,
    val initials: String?,
    val firstName: String?,
    val prefix: String?,
    val lastName: String?,
    val newsletter: Boolean,
    val addressId: Long?,
    val discord: String?,
    val phoneNumber: String?,
    val version: Long?
) : Command<User>

data class FindUsersCommand(
    val filter: UserFilter,
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
