package net.blueshell.api.mapper;

import net.blueshell.api.dto.BrevoContactDTO;
import net.blueshell.api.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.Map;

@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface BrevoContactMapper {

    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "sms", source = "phoneNumber")
    @Mapping(target = "whatsApp", source = "phoneNumber")
    @Mapping(target = "extId", source = "id")
    @Mapping(target = "newsletter", source = "newsletter")
    @Mapping(target = "incasso", source = "incasso")
    @Mapping(target = "memberType", source = "memberType")
    @Mapping(target = "surnamePrefix", source = "prefix")
    @Mapping(target = "country", source = "country")
    @Mapping(target = "city", source = "city")
    @Mapping(target = "street", source = "street")
    @Mapping(target = "email", source = "email")
    BrevoContactDTO toDTO(User user);

    default Map<String, Object> userToAttributes(User user) {
        BrevoContactDTO dto = toDTO(user);
        return dto.toMap();
    }

    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "phoneNumber", source = "sms")
    @Mapping(target = "newsletter", source = "newsletter")
    @Mapping(target = "prefix", source = "surnamePrefix")
    @Mapping(target = "country", source = "country")
    @Mapping(target = "city", source = "city")
    @Mapping(target = "street", source = "street")
    @Mapping(target = "email", source = "email")
    void fromDTO(BrevoContactDTO dto, @MappingTarget User user);

    default void attributesToUser(Map<String, Object> attributes, @MappingTarget User user) {
        BrevoContactDTO dto = new BrevoContactDTO();
        dto.setFirstName((String) attributes.get("FIRSTNAME"));
        dto.setLastName((String) attributes.get("LASTNAME"));
        dto.setSms((String) attributes.get("SMS"));
        dto.setWhatsApp((String) attributes.get("WHATSAPP"));
        dto.setExtId((String) attributes.get("EXT_ID"));
        dto.setNewsletter((Boolean) attributes.get("NEWSLETTER"));
        dto.setIncasso((Boolean) attributes.get("INCASSO"));
        dto.setMemberType((String) attributes.get("MEMBER_TYPE"));
        dto.setSurnamePrefix((String) attributes.get("SURNAME_PREFIX"));
        dto.setCountry((String) attributes.get("COUNTRY"));
        dto.setCity((String) attributes.get("CITY"));
        dto.setStreet((String) attributes.get("STREET"));
        dto.setEmail((String) attributes.get("EMAIL"));
        fromDTO(dto, user);
    }
}