package net.blueshell.api.mapper.user;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.user.AdvancedUserDTO;
import net.blueshell.api.dto.user.SimpleUserDTO;
import net.blueshell.api.model.User;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class SimpleUserMapper extends BaseMapper<User, SimpleUserDTO> {
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    public abstract SimpleUserDTO toDTO(User user);

    @ObjectFactory
    protected User newUser(@TargetType Class<User> type, SimpleUserDTO dto) {
        return new User();
    }

    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "committeeMembers", ignore = true)
    @Mapping(target = "contributions", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "resetKey", ignore = true)
    @Mapping(target = "resetKeyValidUntil", ignore = true)
    @Mapping(target = "resetType", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "password", ignore = true)
    public abstract User fromDTO(SimpleUserDTO dto);

    @AfterMapping
    protected void afterFromDTO(AdvancedUserDTO dto, @MappingTarget User user) {
        if (user.getId() != null) return;

        user.setPassword(passwordEncoder.encode(dto.getPassword()));
    }
}
