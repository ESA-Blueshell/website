package net.blueshell.api.mapper.user;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.user.SimpleUserDTO;
import net.blueshell.api.model.User;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mapstruct.NullValuePropertyMappingStrategy.IGNORE;

@Slf4j
@Mapper(componentModel = "spring")
public abstract class SimpleUserMapper extends BaseMapper<User, SimpleUserDTO> {
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
    @Mapping(target = "phoneNumber")
    @Mapping(target = "newsletter")
    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "version")
    @Mapping(target = "addressId")
    @BeanMapping(ignoreByDefault = true)
    public abstract SimpleUserDTO toDTO(User user);

    @Mapping(target = "initials", conditionExpression = "java(user.getId() == null)")
    @Mapping(target = "firstName", conditionExpression = "java(user.getId() == null)")
    @Mapping(target = "prefix", conditionExpression = "java(user.getId() == null)")
    @Mapping(target = "lastName", conditionExpression = "java(user.getId() == null)")
    @Mapping(target = "username", conditionExpression = "java(user.getId() == null)")
    @Mapping(target = "email", conditionExpression = "java(user.getId() == null)")
    @Mapping(target = "discord")
    @Mapping(target = "phoneNumber")
    @Mapping(target = "newsletter")
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = IGNORE)
    public abstract User fromDTO(SimpleUserDTO dto, @MappingTarget User user);

    @AfterMapping
    protected void afterFromDTO(SimpleUserDTO dto, @MappingTarget User user) {
        if (user.getId() != null) return;

        user.setPassword(passwordEncoder.encode(dto.getPassword()));
    }
}
