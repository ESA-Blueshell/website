package net.blueshell.api.domain.user.command

import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.persistence.filter.UserFilter
import net.blueshell.api.domain.user.web.dto.AdvancedUserDTO
import net.blueshell.api.domain.user.web.dto.SimpleUserDTO
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.Role
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

data class CreateUserCommand(
    val dto: AdvancedUserDTO,
    val isBoard: Boolean
) : Command<User>

data class CreateGuestUserCommand(
    val dto: SimpleUserDTO
) : Command<User>

data class UpdateGuestUserCommand(
    val id: Long,
    val dto: SimpleUserDTO
) : Command<User>

data class UpdateUserCommand(
    val id: Long,
    val dto: AdvancedUserDTO
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
