package net.blueshell.api.mapper.activation;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.request.UserActivationRequest;
import net.blueshell.api.model.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class UserActivationRequestMapper extends BaseMapper<User, UserActivationRequest> {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "enabled", constant = "true")
    @Mapping(target = "resetKey", expression = "java(null)")
    @Mapping(target = "resetType", expression = "java(null)")
    @Mapping(target = "resetKeyValidUntil", expression = "java(null)")
    public abstract User fromDTO(UserActivationRequest dto, @MappingTarget User user);
}

