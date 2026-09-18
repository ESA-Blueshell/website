package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.api.UserUseCases
import net.blueshell.api.user.domain.RoleGrantUseCases
import net.blueshell.api.user.domain.UserQuery
import net.blueshell.api.security.SecurityUtils
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.web.AdvancedController
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping
@Tag(name = "Users")
class UserController(
    service: UserService,
    private val useCases: UserUseCases,
    private val roleGrants: RoleGrantUseCases,
) : AdvancedController<UserService>(
    service
) {
    @PostMapping("/users")
    @PreAuthorize("hasPermission('__NO_TARGET__', 'User', 'write')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@RequestBody @Valid request: CreateUserRequest): UserDetailResponse {
        val user = useCases.create(request.asData(), isBoard = true)
        return user.asDetailResponse()
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasPermission(#id, 'User', 'write')")
    fun updateUser(
        @PathVariable(required = true) id: Long,
        @RequestBody(required = true) payload: UpdateUserRequest,
    ): UserDetailResponse {
        // Taken from the security context rather than bound as a request parameter, so the
        // board check reads a server-held value only.
        val isBoard = SecurityUtils.hasAuthority(Role.BOARD)

        // Board members can update all fields except the password, while users can only update a subset of their
        // own fields. This is enforced by using different command objects for board vs regular updates.
        val user = when (payload) {
            is BoardUpdateUserRequest -> {
                if (!isBoard) throw AccessDeniedException("Board role required")
                useCases.boardUpdate(id, payload.asBoardData())
            }

            is UpdateUserRequest -> useCases.update(id, payload.asData())
        }

        return user.asDetailResponse()
    }

    @GetMapping("/users")
    @PreAuthorize("hasPermission('__NO_TARGET__', 'User', 'read')")
    fun findUsers(
        @ParameterObject query: UserQuery = UserQuery(),
        @ParameterObject pageable: Pageable = Pageable.unpaged()
    ): Page<UserDetailResponse> {
        val users = useCases.findByQuery(query, pageable)
        return users.map { it.asDetailResponse() }
    }

    @GetMapping(value = ["/users/{userId}"])
    @PreAuthorize("hasPermission(#userId, 'User', 'read')")
    fun findUserById(@PathVariable userId: Long): UserDetailResponse {
        val user = useCases.findById(userId)
        return user.asDetailResponse()
    }

    @GetMapping("/users/deleted")
    @PreAuthorize("hasPermission('__NO_TARGET__', 'User', 'read')")
    fun findDeletedUsers(
        @ParameterObject pageable: Pageable = Pageable.unpaged()
    ): Page<UserDetailResponse> {
        val users = useCases.findDeleted(pageable)
        return users.map { it.asDetailResponse() }
    }

    @DeleteMapping(value = ["/users/{userId}"])
    @PreAuthorize("hasPermission(#userId, 'User', 'delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUserById(@PathVariable userId: Long) {
        useCases.delete(userId)
    }

    @PutMapping(value = ["/users/{userId}/restore"])
    @PreAuthorize("hasPermission('__NO_TARGET__', 'User', 'delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun restoreDeletedUserById(@PathVariable userId: Long) {
        useCases.restore(userId)
    }

    @GetMapping(value = ["/users/{userId}/roles"])
    @PreAuthorize("hasPermission(#userId, 'User', 'roles')")
    fun findUserRoles(@PathVariable userId: Long): UserRolesResponse =
        roleGrants.readRoles(userId).asResponse()

    @PutMapping(value = ["/users/{userId}/roles"])
    @PreAuthorize("hasPermission(#userId, 'User', 'roles')")
    fun setUserRoles(
        @PathVariable userId: Long,
        @RequestBody @Valid request: UpdateUserRolesRequest,
    ): UserRolesResponse =
        roleGrants.setGrantedRoles(userId, request.roles, request.note).asResponse()

    @GetMapping(value = ["/users/{userId}/role-changes"])
    @PreAuthorize("hasPermission(#userId, 'User', 'roles')")
    fun findUserRoleChanges(@PathVariable userId: Long): List<RoleChangeResponse> =
        roleGrants.readHistory(userId).map { it.asResponse() }
}
