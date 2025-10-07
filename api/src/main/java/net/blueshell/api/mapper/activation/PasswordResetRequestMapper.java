package net.blueshell.api.mapper.activation;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.request.PasswordResetRequest;
import net.blueshell.api.model.User;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@Mapper(componentModel = "spring")
public abstract class PasswordResetRequestMapper extends BaseMapper<User, PasswordResetRequest> {
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "resetKey", expression = "java(null)")
    @Mapping(target = "resetType", expression = "java(null)")
    @Mapping(target = "resetKeyValidUntil", expression = "java(null)")
    public abstract User fromDTO(PasswordResetRequest dto, @MappingTarget User user);

    @AfterMapping
    protected void afterFromDTO(PasswordResetRequest dto, @MappingTarget User user) {
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
    }
}