package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.api.UserUseCases
import net.blueshell.api.user.domain.UserQuery
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
    private val useCases: UserUseCases,
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
    // CodeQL false positive: `principal`/`isBoard` derive from the server-validated session, not request input; access is gated by @PreAuthorize.
    @Suppress("codeql[java/user-controlled-bypass]")
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

    @PutMapping(value = ["/users/{userId}/roles"])
    @PreAuthorize("hasPermission(#userId, 'User', 'roles')")
    fun toggleUserRole(
        @PathVariable userId: Long,
        @RequestParam(value = "role") role: Role
    ): UserDetailResponse {
        val user = useCases.toggleRole(userId, role)
        return user.asDetailResponse()
    }
}
