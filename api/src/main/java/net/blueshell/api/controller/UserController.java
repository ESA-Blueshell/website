package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.PathParam;
import net.blueshell.api.base.AdvancedController;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.user.AdvancedUserDTO;
import net.blueshell.api.dto.user.SimpleUserDTO;
import net.blueshell.api.mapper.user.AdvancedUserMapper;
import net.blueshell.api.mapper.user.SimpleUserMapper;
import net.blueshell.api.model.User;
import net.blueshell.api.service.UserService;
import net.blueshell.api.validation.group.Administration;
import net.blueshell.api.validation.group.Creation;
import net.blueshell.api.validation.group.Update;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
@Tag(name = "Users")
public class UserController extends AdvancedController<UserService, AdvancedUserMapper, SimpleUserMapper> {

    @Autowired
    public UserController(UserService service, AdvancedUserMapper advancedUserMapper, SimpleUserMapper simpleUserMapper) {
        super(service, advancedUserMapper, simpleUserMapper);
    }

    @PostMapping("/users")
    public AdvancedUserDTO createUser(@Validated(Creation.class) @RequestBody AdvancedUserDTO dto) {
        User user = advancedMapper.fromDTO(dto);
        service.create(user);
        return advancedMapper.toDTO(user);
    }

    @PostMapping("/users/member")
    public AdvancedUserDTO createMember(@Validated(Administration.class) @RequestBody AdvancedUserDTO dto) {
        User user = advancedMapper.fromDTO(dto);
        service.create(user);
        return advancedMapper.toDTO(user);
    }

    @PostMapping("/users/guest")
    public SimpleUserDTO createGuestUser(@Validated(Creation.class) @RequestBody SimpleUserDTO dto) {
        User user = simpleMapper.fromDTO(dto);
        service.create(user);
        return simpleMapper.toDTO(user);
    }

    @PutMapping(value = "/users/{userId}")
    @PreAuthorize("hasPermission(#userId, 'User', 'write')")
    public AdvancedUserDTO updateUser(@PathVariable("userId") Long userId,
                                      @Validated(Update.class) @RequestBody AdvancedUserDTO dto) {
        dto.setId(userId);
        User user = advancedMapper.fromDTO(dto);
        service.update(user);
        return advancedMapper.toDTO(user);
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('BOARD')")
    public List<AdvancedUserDTO> findUsers() {
        List<User> users = service.findAll();
        return advancedMapper.toDTOs(users);
    }

    @GetMapping(value = "/users/{userId}")
    @PreAuthorize("hasPermission(#userId, 'User', 'read')")
    public AdvancedUserDTO findUserById(@PathVariable("userId") Long userId) {
        User user = service.findById(userId);
        return advancedMapper.toDTO(user);
    }

    @DeleteMapping(value = "/users/{userId}")
    @PreAuthorize("hasPermission(#userId, 'User', 'delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUserById(@PathVariable("userId") Long userId) {
        service.delete(userId);
    }

    @PutMapping(value = "/users/{userId}/roles")
    @PreAuthorize("hasPermission(#userId, 'User', 'changeRole')")
    public AdvancedUserDTO toggleUserRole(@PathVariable("userId") Long userId,
                                          @RequestParam(value = "role") Role role) {
        User user = service.toggleRole(userId, role);
        return advancedMapper.toDTO(user);
    }
}
