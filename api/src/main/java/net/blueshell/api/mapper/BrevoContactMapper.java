package net.blueshell.api.mapper;

import net.blueshell.api.common.enums.Role;
import net.blueshell.api.model.User;
import net.blueshell.clients.brevo.model.CreateContact;
import net.blueshell.clients.brevo.model.UpdateContact;
import org.mapstruct.*;

import java.util.HashMap;
import java.util.Map;

@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface BrevoContactMapper {

    @Mapping(target = "email", source = "email")
    @Mapping(target = "extId", source = "id")
    @Mapping(target = "attributes", source = ".", qualifiedByName = "toAttributes")
    @BeanMapping(ignoreByDefault = true)
    CreateContact toCreate(User user);

    @Mapping(target = "extId", source = "id")
    @Mapping(target = "attributes", source = ".", qualifiedByName = "toAttributes")
    @BeanMapping(ignoreByDefault = true)
    UpdateContact toUpdate(User user);

    @Named("toAttributes")
    default Map<String, Object> toAttributes(User user) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("NEWSLETTER", user.isNewsletter());
        attrs.put("INCASSO", user.getIncasso());
        attrs.put("IS_MEMBER", user.hasRole(Role.MEMBER));
        attrs.put("FIRSTNAME", user.getFirstName());
        attrs.put("LASTNAME", user.getLastName());
        attrs.put("SURNAME", user.getLastName());
        attrs.put("SMS", user.getPhoneNumber());
        attrs.put("WHATSAPP", user.getPhoneNumber());
        attrs.put("MEMBER_TYPE", user.getMemberType());
        return attrs;
    }
}