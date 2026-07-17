package net.blueshell.api.domain.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.query.UserQuery
import net.blueshell.api.domain.user.command.DeleteUserByIdCommand
import net.blueshell.api.domain.user.command.FindUserByIdCommand
import net.blueshell.api.domain.user.command.FindDeletedUsersCommand
import net.blueshell.api.domain.user.command.FindUsersCommand
import net.blueshell.api.domain.user.command.RestoreDeletedUserByIdCommand
import net.blueshell.api.domain.user.command.ToggleUserRoleCommand
import net.blueshell.api.domain.user.web.dto.request.BoardUpdateUserRequest
import net.blueshell.api.domain.user.web.dto.request.CreateUserRequest
import net.blueshell.api.domain.user.web.dto.request.UpdateUserRequest
import net.blueshell.api.domain.user.web.dto.response.UserDetailResponse
import net.blueshell.api.domain.user.web.mapping.request.asCommand
import net.blueshell.api.domain.user.web.mapping.response.asDetailResponse
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.shared.web.AdvancedController
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
@Tag(name = "Users")
class UserController(
    service: UserService,
    private val commandBus: CommandBus
) : AdvancedController<UserService>(
    service
) {
    @PostMapping("/users")
    @PermitAll
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(
        @RequestBody @Valid request: CreateUserRequest,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): UserDetailResponse {
        // Block non-BOARD authenticated users from creating accounts
        // Allows: anonymous users (principal == null) and BOARD users
        // Denies: MEMBER, GUEST, COMMITTEE, etc.
        if (principal != null && !principal.hasAuthority(Role.BOARD)) {
            throw AccessDeniedException("Only anonymous users or BOARD members can create users")
        }

        val isBoard = principal?.hasAuthority(Role.BOARD) == true
        val user = commandBus.dispatch(request.asCommand(isBoard))
        return user.asDetailResponse()
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasPermission(#id, 'User', 'write')")
    fun updateUser(
        @PathVariable(required = true) id: Long,
        @RequestBody(required = true) payload: UpdateUserRequest,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): UserDetailResponse {
        val isBoard = principal?.hasAuthority(Role.BOARD) == true

        // Board members can update all fields except the password, while users can only update a subset of their
        // own fields. This is enforced by using different command objects for board vs regular updates.
        val user = when (payload) {
            is BoardUpdateUserRequest -> {
                if (!isBoard) throw AccessDeniedException("Board role required")
                commandBus.dispatch(payload.asBoardCommand(id))
            }

            is UpdateUserRequest -> commandBus.dispatch(payload.asCommand(id))
        }

        return user.asDetailResponse()
    }

    @GetMapping("/users")
    @PreAuthorize("hasPermission('__NO_TARGET__', 'User', 'read')")
    fun findUsers(
        @ParameterObject query: UserQuery = UserQuery(),
        @ParameterObject pageable: Pageable = Pageable.unpaged()
    ): Page<UserDetailResponse> {
        val users = commandBus.dispatch(FindUsersCommand(query, pageable))
        return users.map { it.asDetailResponse() }
    }

    @GetMapping(value = ["/users/{userId}"])
    @PreAuthorize("hasPermission(#userId, 'User', 'read')")
    fun findUserById(@PathVariable userId: Long): UserDetailResponse {
        val user = commandBus.dispatch(FindUserByIdCommand(userId))
        return user.asDetailResponse()
    }

    @GetMapping("/users/deleted")
    @PreAuthorize("hasPermission('__NO_TARGET__', 'User', 'read')")
    fun findDeletedUsers(
        @ParameterObject pageable: Pageable = Pageable.unpaged()
    ): Page<UserDetailResponse> {
        val users = commandBus.dispatch(FindDeletedUsersCommand(pageable))
        return users.map { it.asDetailResponse() }
    }

    @DeleteMapping(value = ["/users/{userId}"])
    @PreAuthorize("hasPermission(#userId, 'User', 'delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUserById(@PathVariable userId: Long) {
        commandBus.dispatch(DeleteUserByIdCommand(userId))
    }

    @PutMapping(value = ["/users/{userId}/restore"])
    @PreAuthorize("hasPermission('__NO_TARGET__', 'User', 'delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun restoreDeletedUserById(@PathVariable userId: Long) {
        commandBus.dispatch(RestoreDeletedUserByIdCommand(userId))
    }

    @PutMapping(value = ["/users/{userId}/roles"])
    @PreAuthorize("hasPermission(#userId, 'User', 'roles')")
    fun toggleUserRole(
        @PathVariable userId: Long,
        @RequestParam(value = "role") role: Role
    ): UserDetailResponse {
        val user = commandBus.dispatch(ToggleUserRoleCommand(userId, role))
        return user.asDetailResponse()
    }
}
