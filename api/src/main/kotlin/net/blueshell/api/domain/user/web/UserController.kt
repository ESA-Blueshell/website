package net.blueshell.api.domain.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.web.AdvancedController
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.persistence.filter.UserFilter
import net.blueshell.api.domain.user.web.dto.CreateGuestUserRequest
import net.blueshell.api.domain.user.web.dto.CreateUserRequest
import net.blueshell.api.domain.user.web.dto.UpdateGuestUserRequest
import net.blueshell.api.domain.user.web.dto.UpdateUserRequest
import net.blueshell.api.domain.user.web.dto.UserDetailResponse
import net.blueshell.api.domain.user.web.dto.UserSummaryResponse
import net.blueshell.api.domain.user.web.mapping.asDetailResponse
import net.blueshell.api.domain.user.web.mapping.asEntity
import net.blueshell.api.domain.user.web.mapping.asSummaryResponse
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
@Tag(name = "Users")
class UserController(
    service: UserService,
    private val validator: Validator,
    private val passwordEncoder: PasswordEncoder
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

        var user = request.asEntity(User(), passwordEncoder)

        user = service.create(user)
        return user.asDetailResponse()
    }

    @PostMapping("/users/guest")
    @PermitAll
    @ResponseStatus(HttpStatus.CREATED)
    fun createGuestUser(@Validated(net.blueshell.api.shared.validation.group.Creation::class) @RequestBody request: CreateGuestUserRequest): UserSummaryResponse {
        var user = request.asEntity(User(), passwordEncoder)
        user = service.create(user)
        return user.asSummaryResponse()
    }

    @PutMapping("/users/guest/{id}")
    @PermitAll
    fun updateGuestUser(
        @PathVariable id: Long,
        @Validated(net.blueshell.api.shared.validation.group.Update::class) @RequestBody request: UpdateGuestUserRequest
    ): UserSummaryResponse {
        var user = service.findById(id)
        request.asEntity(user, passwordEncoder)
        user = service.update(user)
        return user.asSummaryResponse()
    }

    @PutMapping(value = ["/users/{id}"])
    @PreAuthorize("#dto.id == #id && (hasAuthority('BOARD') || hasPermission(#id, 'User', 'write'))")
    fun updateUser(
        @PathVariable id: Long,
        @Validated(net.blueshell.api.shared.validation.group.Update::class) @RequestBody request: UpdateUserRequest
    ): UserDetailResponse {
        var user = service.findById(id)
        request.asEntity(user, passwordEncoder)
        user = service.update(user)
        return user.asDetailResponse()
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('BOARD')")
    fun findUsers(
        @ParameterObject filter: UserFilter = UserFilter(),
        @ParameterObject pageable: Pageable = Pageable.unpaged()
    ): Page<UserDetailResponse> {
        val users = service.findByFilter(filter, pageable)
        return users.map { it.asDetailResponse() }
    }

    @GetMapping(value = ["/users/{userId}"])
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#userId, 'User', 'read')")
    fun findUserById(@PathVariable userId: Long): UserDetailResponse {
        val user = service.findById(userId)
        return user.asDetailResponse()
    }

    @DeleteMapping(value = ["/users/{userId}"])
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUserById(@PathVariable userId: Long) {
        service.deleteById(userId)
    }

    @PutMapping(value = ["/users/{userId}/roles"])
    @PreAuthorize("hasAuthority('ADMIN')")
    fun toggleUserRole(
        @PathVariable userId: Long,
        @RequestParam(value = "role") role: Role
    ): UserDetailResponse {
        val user = service.toggleRole(userId, role)
        return user.asDetailResponse()
    }
}
