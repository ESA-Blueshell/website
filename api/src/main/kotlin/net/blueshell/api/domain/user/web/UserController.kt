package net.blueshell.api.domain.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.query.UserQuery
import net.blueshell.api.domain.user.command.*
import net.blueshell.api.domain.user.web.dto.*
import net.blueshell.api.domain.user.web.mapping.asCommand
import net.blueshell.api.domain.user.web.mapping.asDetailResponse
import net.blueshell.api.domain.user.web.mapping.asSummaryResponse
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.web.AdvancedController
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
@Tag(name = "Users")
class UserController(
    service: UserService,
    private val validator: Validator,
    private val commandBus: CommandBus
) : AdvancedController<UserService>(
    service
) {
    @PostMapping("/users")
    @PermitAll
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(
        @RequestBody request: CreateUserRequest,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): UserDetailResponse {
        // Block non-BOARD authenticated users from creating accounts
        // Allows: anonymous users (principal == null) and BOARD users
        // Denies: MEMBER, GUEST, COMMITTEE, etc.
        if (principal != null && !principal.hasAuthority(Role.BOARD)) {
            throw AccessDeniedException("Only anonymous users or BOARD members can create users")
        }

        val isBoard = principal?.hasAuthority(Role.BOARD) == true
        val groups: Array<Class<*>> = if (isBoard)
            arrayOf(net.blueshell.api.shared.validation.group.Administration::class.java)
        else
            arrayOf(net.blueshell.api.shared.validation.group.Creation::class.java)

        val violations = validator.validate(request, *groups)
        if (!violations.isEmpty()) {
            throw ConstraintViolationException(violations)
        }

        val user = commandBus.dispatch(request.asCommand(isBoard))
        return user.asDetailResponse()
    }

    @PostMapping("/users/guest")
    @PermitAll
    @ResponseStatus(HttpStatus.CREATED)
    fun createGuestUser(@Validated(net.blueshell.api.shared.validation.group.Creation::class) @RequestBody request: CreateGuestUserRequest): UserSummaryResponse {
        val user = commandBus.dispatch(request.asCommand())
        return user.asSummaryResponse()
    }

    @PutMapping("/users/guest/{id}")
    @PreAuthorize("hasPermission(#id, 'User', 'write')")
    fun updateGuestUser(
        @PathVariable id: Long,
        @Validated(net.blueshell.api.shared.validation.group.Update::class) @RequestBody request: UpdateGuestUserRequest
    ): UserSummaryResponse {
        val user = commandBus.dispatch(request.asCommand(id))
        return user.asSummaryResponse()
    }

    @PutMapping(value = ["/users/{id}"])
    @PreAuthorize("hasPermission(#id, 'User', 'write')")
    fun updateUser(
        @PathVariable id: Long,
        @Validated(net.blueshell.api.shared.validation.group.Update::class) @RequestBody request: UpdateUserRequest,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): UserDetailResponse {
        val isBoard = principal?.hasAuthority(Role.BOARD) == true
        val user = commandBus.dispatch(request.asCommand(id, isBoard))
        return user.asDetailResponse()
    }

    @GetMapping("/users")
    @PreAuthorize("hasPermission(null, 'User', 'board')")
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

    @DeleteMapping(value = ["/users/{userId}"])
    @PreAuthorize("hasPermission(null, 'User', 'board')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUserById(@PathVariable userId: Long) {
        commandBus.dispatch(DeleteUserByIdCommand(userId))
    }

    @PutMapping(value = ["/users/{userId}/roles"])
    @PreAuthorize("hasPermission(null, 'User', 'admin')")
    fun toggleUserRole(
        @PathVariable userId: Long,
        @RequestParam(value = "role") role: Role
    ): UserDetailResponse {
        val user = commandBus.dispatch(ToggleUserRoleCommand(userId, role))
        return user.asDetailResponse()
    }
}
