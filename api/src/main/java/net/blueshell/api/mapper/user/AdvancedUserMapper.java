package net.blueshell.api.mapper.user;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.user.AdvancedUserDTO;
import net.blueshell.api.model.User;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static net.blueshell.api.common.util.MappingUtil.applyIfFieldIsNotNull;
import static net.blueshell.api.common.util.MappingUtil.generatePassword;
import static org.mapstruct.NullValuePropertyMappingStrategy.IGNORE;

@Mapper(componentModel = "spring")
public abstract class AdvancedUserMapper extends BaseMapper<User, AdvancedUserDTO> {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Mapping(target = "id")
    @Mapping(target = "initials")
    @Mapping(target = "firstName")
    @Mapping(target = "prefix")
    @Mapping(target = "lastName")
    @Mapping(target = "username")
    @Mapping(target = "discord")
    @Mapping(target = "email")
    @Mapping(target = "dateOfBirth")
    @Mapping(target = "phoneNumber")
    @Mapping(target = "nationality")
    @Mapping(target = "photoConsent")
    @Mapping(target = "bhv")
    @Mapping(target = "ehbo")
    @Mapping(target = "enabled")
    @Mapping(target = "createdAt")
    @Mapping(target = "gender")
    @Mapping(target = "studentNumber")
    @Mapping(target = "addressId")
    @Mapping(target = "newsletter")
    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "roles", expression = "java(user.getInheritedRoles())")
    @Mapping(target = "password", ignore = true)
    @BeanMapping(ignoreByDefault = true)
    public abstract AdvancedUserDTO toDTO(User user);

    @Mapping(target = "initials", ignore = true)
    @Mapping(target = "firstName", ignore = true)
    @Mapping(target = "prefix", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "discord")
    @Mapping(target = "dateOfBirth")
    @Mapping(target = "phoneNumber")
    @Mapping(target = "nationality")
    @Mapping(target = "photoConsent")
    @Mapping(target = "bhv")
    @Mapping(target = "ehbo")
    @Mapping(target = "newsletter")
    @Mapping(target = "gender")
    @Mapping(target = "studentNumber")
    @Mapping(target = "addressId")
    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = IGNORE)
    public abstract User fromDTO(AdvancedUserDTO dto, @MappingTarget User user);

    @AfterMapping
    protected void afterFromDTO(AdvancedUserDTO dto, @MappingTarget User user) {
        if (user.getId() != null) return;

        if (hasAuthority(Role.BOARD)) {
            user.setCreatorId(getPrincipal().getId());
            user.setPassword(passwordEncoder.encode(generatePassword()));
        } else {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        applyIfFieldIsNotNull(user, dto.getInitials(), User::setInitials);
        applyIfFieldIsNotNull(user, dto.getFirstName(), User::setFirstName);
        applyIfFieldIsNotNull(user, dto.getPrefix(), User::setPrefix);
        applyIfFieldIsNotNull(user, dto.getLastName(), User::setLastName);
        applyIfFieldIsNotNull(user, dto.getUsername(), User::setUsername);
        applyIfFieldIsNotNull(user, dto.getEmail(), User::setEmail);
    }
}
