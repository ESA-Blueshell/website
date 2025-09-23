package net.blueshell.api.mapper.user;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.user.AdvancedUserDTO;
import net.blueshell.api.dto.user.SimpleUserDTO;
import net.blueshell.api.model.User;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class SimpleUserMapper extends BaseMapper<User, SimpleUserDTO> {
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "id")
    @BeanMapping(ignoreByDefault = true)
    public abstract SimpleUserDTO toDTO(User user);

    @ObjectFactory
    protected User newUser(@TargetType Class<User> type, SimpleUserDTO dto) {
        return new User();
    }


    @BeanMapping(ignoreByDefault = true)
    public abstract User fromDTO(SimpleUserDTO dto, @MappingTarget User user);

    @AfterMapping
    protected void afterFromDTO(SimpleUserDTO dto, @MappingTarget User user) {
        if (user.getId() != null) return;

        user.setPassword(passwordEncoder.encode(dto.getPassword()));
    }
}
