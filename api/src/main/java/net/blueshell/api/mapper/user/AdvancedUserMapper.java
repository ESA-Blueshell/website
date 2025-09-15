package net.blueshell.api.mapper.user;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.user.AdvancedUserDTO;
import net.blueshell.api.model.User;
import net.blueshell.api.service.UserService;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static net.blueshell.api.common.util.MappingUtil.applyIfFieldIsNotNull;
import static net.blueshell.api.common.util.MappingUtil.generatePassword;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class AdvancedUserMapper extends BaseMapper<User, AdvancedUserDTO> {

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserService users;

    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "roles", expression = "java(user.getInheritedRoles())")
    @Mapping(target = "password", ignore = true)
    public abstract AdvancedUserDTO toDTO(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "initials")
    @Mapping(target = "firstName")
    @Mapping(target = "prefix")
    @Mapping(target = "lastName")
    @Mapping(target = "username", conditionExpression = "java(hasAuthority(net.blueshell.api.common.enums.Role.BOARD))")
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "email")
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "committeeMembers", ignore = true)
    @Mapping(target = "contributions", ignore = true)
    @Mapping(target = "resetKey", ignore = true)
    @Mapping(target = "resetKeyValidUntil", ignore = true)
    @Mapping(target = "resetType", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "creator", ignore = true)
    public abstract User fromDTO(AdvancedUserDTO dto);

    @ObjectFactory
    protected User newUser(@TargetType Class<User> type, AdvancedUserDTO dto) {
        if (dto.getId() != null) {
            return users.findById(dto.getId());
        }
        return new User();
    }

    @AfterMapping
    protected void afterFromDTO(AdvancedUserDTO dto, @MappingTarget User user) {
        // Return if user update
        // Return if not board
        if (user.getId() != null && !hasAuthority(Role.BOARD)) {
            return;
        }

        applyIfFieldIsNotNull(user, dto.getInitials(), User::setInitials);
        applyIfFieldIsNotNull(user, dto.getFirstName(), User::setFirstName);
        applyIfFieldIsNotNull(user, dto.getPrefix(), User::setPrefix);
        applyIfFieldIsNotNull(user, dto.getLastName(), User::setLastName);
        applyIfFieldIsNotNull(user, dto.getEmail(), User::setEmail);

        // If a user is creating their account, set the password.
        // Then return
        if (!hasAuthority(Role.BOARD)) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
            return;
        }

        // If creating a new user as a board member, set creator and generate random password.
        if (user.getId() == null) {
            user.setCreatorId(getPrincipal().getId());
            user.setPassword(passwordEncoder.encode(generatePassword()));
        }
    }
}
