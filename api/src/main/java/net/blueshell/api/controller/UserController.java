package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import net.blueshell.api.base.AdvancedController;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.controller.filter.UserFilter;
import net.blueshell.api.dto.user.AdvancedUserDTO;
import net.blueshell.api.dto.user.SimpleUserDTO;
import net.blueshell.api.mapper.user.AdvancedUserMapper;
import net.blueshell.api.mapper.user.SimpleUserMapper;
import net.blueshell.api.model.User;
import net.blueshell.api.service.UserService;
import net.blueshell.api.validation.group.Administration;
import net.blueshell.api.validation.group.Creation;
import net.blueshell.api.validation.group.Update;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping
@Tag(name = "Users")
public class UserController extends AdvancedController<UserService, AdvancedUserMapper, SimpleUserMapper> {

    @Autowired
    public UserController(UserService service, AdvancedUserMapper advancedUserMapper, SimpleUserMapper simpleUserMapper) {
        super(service, advancedUserMapper, simpleUserMapper);
    }

    @PostMapping("/users")
    @PermitAll
    @ResponseStatus(HttpStatus.CREATED)
    public AdvancedUserDTO createUser(@Validated(Creation.class) @RequestBody AdvancedUserDTO dto) {
        var user = advancedMapper.fromDTO(dto);
        user = service.create(user);
        return advancedMapper.toDTO(user);
    }

    @PostMapping("/users/member")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BOARD')")
    public AdvancedUserDTO createMember(@Validated(Administration.class) @RequestBody AdvancedUserDTO dto) {
        var user = advancedMapper.fromDTO(dto);
        user = service.create(user);
        return advancedMapper.toDTO(user);
    }

    @PostMapping("/users/guest")
    @PermitAll
    @ResponseStatus(HttpStatus.CREATED)
    public SimpleUserDTO createGuestUser(@Validated(Creation.class) @RequestBody SimpleUserDTO dto) {
        var user = simpleMapper.fromDTO(dto, new User());
        user = service.create(user);
        return simpleMapper.toDTO(user);
    }

    @PutMapping(value = "/users/{id}")
    @PreAuthorize("#dto.id == #id && (hasAuthority('BOARD') || hasPermission(#id, 'User', 'write'))")
    public AdvancedUserDTO updateUser(@PathVariable("id") Long id,
                                      @Validated(Update.class) @RequestBody AdvancedUserDTO dto) {
        var user = service.findById(id);
        advancedMapper.fromDTO(dto, user);
        user = service.update(user);
        return advancedMapper.toDTO(user);
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('BOARD')")
    public Page<AdvancedUserDTO> findUsers(@ParameterObject UserFilter filter, @ParameterObject Pageable pageable) {
        var users = service.findByFilter(filter, pageable);
        return advancedMapper.toDTOs(users);
    }

    @GetMapping(value = "/users/{userId}")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#userId, 'User', 'read')")
    public AdvancedUserDTO findUserById(@PathVariable("userId") Long userId) {
        var user = service.findById(userId);
        return advancedMapper.toDTO(user);
    }

    @DeleteMapping(value = "/users/{userId}")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUserById(@PathVariable("userId") Long userId) {
        service.deleteById(userId);
    }

    @PutMapping(value = "/users/{userId}/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdvancedUserDTO toggleUserRole(@PathVariable("userId") Long userId,
                                          @RequestParam(value = "role") Role role) {
        var user = service.toggleRole(userId, role);
        return advancedMapper.toDTO(user);
    }
}
