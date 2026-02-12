package net.blueshell.api.domain.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.*
import net.blueshell.api.domain.user.persistence.filter.UserFilter
import net.blueshell.api.domain.user.web.dto.*
import net.blueshell.api.domain.user.web.mapping.asDetailResponse
import net.blueshell.api.domain.user.web.mapping.asSummaryResponse
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.web.AdvancedController
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
@Tag(name = "Users")
class UserController(
    service: net.blueshell.api.domain.user.application.UserService,
    private val validator: Validator,
    private val commandBus: CommandBus
) : AdvancedController<UserService>(
    service
) {
    @PostMapping("/users")
    @PermitAll
    @ResponseStatus(HttpStatus.CREATED) // TODO: Once all members are in the site, remove the ability for admins to create new users
    fun createUser(@RequestBody request: CreateUserRequest): UserDetailResponse {
        val groups: Array<Class<*>> = if (hasAuthority(Role.BOARD))
            arrayOf(net.blueshell.api.shared.validation.group.Administration::class.java)
        else
            arrayOf(net.blueshell.api.shared.validation.group.Creation::class.java)

        val violations = validator.validate(request, *groups)
        if (!violations.isEmpty()) {
            throw ConstraintViolationException(violations)
        }

        val isBoard = hasAuthority(Role.BOARD)
        val user = commandBus.dispatch(
            CreateUserCommand(
                isBoard = isBoard,
                roles = request.roles ?: emptySet(),
                dateOfBirth = request.dateOfBirth,
                nationality = request.nationality,
                photoConsent = requireNotNull(request.photoConsent) { "Photo consent is required" },
                ehbo = requireNotNull(request.ehbo) { "EHBO is required" },
                bhv = requireNotNull(request.bhv) { "BHV is required" },
                enabled = request.enabled ?: false,
                gender = request.gender,
                studentNumber = request.studentNumber,
                username = request.username,
                email = request.email,
                initials = request.initials,
                firstName = request.firstName,
                prefix = request.prefix,
                lastName = request.lastName,
                newsletter = requireNotNull(request.newsletter) { "Newsletter is required" },
                password = if (isBoard) null else requireNotNull(request.password) { "Password is required" },
                addressId = request.addressId,
                discord = request.discord,
                phoneNumber = request.phoneNumber
            )
        )
        return user.asDetailResponse()
    }

    @PostMapping("/users/guest")
    @PermitAll
    @ResponseStatus(HttpStatus.CREATED)
    fun createGuestUser(@Validated(net.blueshell.api.shared.validation.group.Creation::class) @RequestBody request: CreateGuestUserRequest): UserSummaryResponse {
        val user = commandBus.dispatch(
            CreateGuestUserCommand(
                username = request.username,
                initials = request.initials,
                firstName = request.firstName,
                prefix = request.prefix,
                lastName = request.lastName,
                newsletter = requireNotNull(request.newsletter) { "Newsletter is required" },
                password = requireNotNull(request.password) { "Password is required" },
                addressId = request.addressId,
                email = request.email,
                discord = request.discord,
                phoneNumber = request.phoneNumber
            )
        )
        return user.asSummaryResponse()
    }

    @PutMapping("/users/guest/{id}")
    @PermitAll
    fun updateGuestUser(
        @PathVariable id: Long,
        @Validated(net.blueshell.api.shared.validation.group.Update::class) @RequestBody request: UpdateGuestUserRequest
    ): UserSummaryResponse {
        val user = commandBus.dispatch(
            UpdateGuestUserCommand(
                id = id,
                discord = request.discord,
                phoneNumber = request.phoneNumber,
                newsletter = requireNotNull(request.newsletter) { "Newsletter is required" },
                version = request.version
            )
        )
        return user.asSummaryResponse()
    }

    @PutMapping(value = ["/users/{id}"])
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'User', 'write')")
    fun updateUser(
        @PathVariable id: Long,
        @Validated(net.blueshell.api.shared.validation.group.Update::class) @RequestBody request: UpdateUserRequest
    ): UserDetailResponse {
        val isBoard = hasAuthority(Role.BOARD)
        val user = commandBus.dispatch(
            UpdateUserCommand(
                id = id,
                isBoard = isBoard,
                roles = request.roles ?: emptySet(),
                dateOfBirth = request.dateOfBirth,
                nationality = request.nationality,
                photoConsent = requireNotNull(request.photoConsent) { "Photo consent is required" },
                ehbo = requireNotNull(request.ehbo) { "EHBO is required" },
                bhv = requireNotNull(request.bhv) { "BHV is required" },
                enabled = request.enabled ?: false,
                gender = request.gender,
                studentNumber = request.studentNumber,
                username = request.username,
                email = request.email,
                initials = request.initials,
                firstName = request.firstName,
                prefix = request.prefix,
                lastName = request.lastName,
                newsletter = requireNotNull(request.newsletter) { "Newsletter is required" },
                addressId = request.addressId,
                discord = request.discord,
                phoneNumber = request.phoneNumber,
                version = request.version
            )
        )
        return user.asDetailResponse()
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('BOARD')")
    fun findUsers(
        @ParameterObject filter: UserFilter = UserFilter(),
        @ParameterObject pageable: Pageable = Pageable.unpaged()
    ): Page<UserDetailResponse> {
        val users = commandBus.dispatch(FindUsersCommand(filter, pageable))
        return users.map { it.asDetailResponse() }
    }

    @GetMapping(value = ["/users/{userId}"])
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#userId, 'User', 'read')")
    fun findUserById(@PathVariable userId: Long): UserDetailResponse {
        val user = commandBus.dispatch(FindUserByIdCommand(userId))
        return user.asDetailResponse()
    }

    @DeleteMapping(value = ["/users/{userId}"])
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUserById(@PathVariable userId: Long) {
        commandBus.dispatch(DeleteUserByIdCommand(userId))
    }

    @PutMapping(value = ["/users/{userId}/roles"])
    @PreAuthorize("hasAuthority('ADMIN')")
    fun toggleUserRole(
        @PathVariable userId: Long,
        @RequestParam(value = "role") role: Role
    ): UserDetailResponse {
        val user = commandBus.dispatch(ToggleUserRoleCommand(userId, role))
        return user.asDetailResponse()
    }
}
