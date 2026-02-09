package net.blueshell.api.feature.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.web.AdvancedController
import net.blueshell.api.feature.user.dto.AdvancedUserDTO
import net.blueshell.api.feature.user.dto.SimpleUserDTO
import net.blueshell.api.feature.user.mapper.AdvancedUserMapper
import net.blueshell.api.feature.user.mapper.SimpleUserMapper
import net.blueshell.api.feature.user.model.User
import net.blueshell.api.feature.user.model.filter.UserFilter
import net.blueshell.api.feature.user.service.UserService
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
    service: UserService,
    advancedUserMapper: AdvancedUserMapper,
    simpleUserMapper: SimpleUserMapper,
    private val validator: Validator
) : AdvancedController<UserService, AdvancedUserMapper, SimpleUserMapper>(
    service,
    advancedUserMapper,
    simpleUserMapper
) {
    @PostMapping("/users")
    @PermitAll
    @ResponseStatus(HttpStatus.CREATED) // TODO: Once all members are in the site, remove the ability for admins to create new users
    fun createUser(@RequestBody dto: AdvancedUserDTO): AdvancedUserDTO {
        val groups: Array<Class<*>> = if (hasAuthority(Role.BOARD))
            arrayOf(net.blueshell.api.shared.validation.group.Administration::class.java)
        else
            arrayOf(net.blueshell.api.shared.validation.group.Creation::class.java)

        val violations = validator.validate(dto, *groups)
        if (!violations.isEmpty()) {
            throw ConstraintViolationException(violations)
        }

        var user = advancedMapper.fromDTO(dto, User())

        user = service.create(user)
        return advancedMapper.toDTO(user)
    }

    @PostMapping("/users/guest")
    @PermitAll
    @ResponseStatus(HttpStatus.CREATED)
    fun createGuestUser(@Validated(net.blueshell.api.shared.validation.group.Creation::class) @RequestBody dto: SimpleUserDTO): SimpleUserDTO {
        var user = simpleMapper.fromDTO(dto, User())
        user = service.create(user)
        return simpleMapper.toDTO(user)
    }

    @PutMapping("/users/guest/{id}")
    @PermitAll
    fun updateGuestUser(
        @PathVariable id: Long,
        @Validated(net.blueshell.api.shared.validation.group.Update::class) @RequestBody dto: SimpleUserDTO
    ): SimpleUserDTO {
        var user = service.findById(id)
        simpleMapper.fromDTO(dto, user)
        user = service.update(user)
        return simpleMapper.toDTO(user)
    }

    @PutMapping(value = ["/users/{id}"])
    @PreAuthorize("#dto.id == #id && (hasAuthority('BOARD') || hasPermission(#id, 'User', 'write'))")
    fun updateUser(
        @PathVariable id: Long,
        @Validated(net.blueshell.api.shared.validation.group.Update::class) @RequestBody dto: AdvancedUserDTO
    ): AdvancedUserDTO {
        var user = service.findById(id)
        advancedMapper.fromDTO(dto, user)
        user = service.update(user)
        return advancedMapper.toDTO(user)
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('BOARD')")
    fun findUsers(
        @ParameterObject filter: UserFilter = UserFilter(),
        @ParameterObject pageable: Pageable = Pageable.unpaged()
    ): Page<AdvancedUserDTO> {
        val users = service.findByFilter(filter, pageable)
        return advancedMapper.toDTOs(users)
    }

    @GetMapping(value = ["/users/{userId}"])
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#userId, 'User', 'read')")
    fun findUserById(@PathVariable userId: Long): AdvancedUserDTO {
        val user = service.findById(userId)
        return advancedMapper.toDTO(user)
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
    ): AdvancedUserDTO {
        val user = service.toggleRole(userId, role)
        return advancedMapper.toDTO(user)
    }
}
