package net.blueshell.api.mapper;

import net.blueshell.api.model.User;
import net.blueshell.api.auth.Identity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class IdentityMapper {

    @Mapping(target = "roles", source = "inheritedRoles")
    public abstract Identity fromUser(User user);
}
