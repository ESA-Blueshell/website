package net.blueshell.api.mapper;

import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.controller.request.ActivationRequest;
import net.blueshell.api.controller.request.PasswordResetRequest;
import net.blueshell.api.model.User;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class RequestMapper {

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Mapping(target = "username", ignore = true)
    @Mapping(target = "resetType", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "firstName", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "prefix", ignore = true)
    @Mapping(target = "initials", ignore = true)
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "houseNumber", ignore = true)
    @Mapping(target = "postalCode", ignore = true)
    @Mapping(target = "city", ignore = true)
    @Mapping(target = "phoneNumber", ignore = true)
    @Mapping(target = "studentNumber", ignore = true)
    @Mapping(target = "dateOfBirth", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "discord", ignore = true)
    @Mapping(target = "steamid", ignore = true)
    @Mapping(target = "newsletter", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "resetKey", ignore = true)
    @Mapping(target = "resetKeyValidUntil", ignore = true)
    @Mapping(target = "consentPrivacy", ignore = true)
    @Mapping(target = "consentGdpr", ignore = true)
    @Mapping(target = "gender", ignore = true)
    @Mapping(target = "street", ignore = true)
    @Mapping(target = "country", ignore = true)
    @Mapping(target = "photoConsent", ignore = true)
    @Mapping(target = "nationality", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "committeeMembers", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "ehbo", ignore = true)
    @Mapping(target = "contactId", ignore = true)
    @Mapping(target = "bhv", ignore = true)
    @Mapping(target = "contributions", ignore = true)
    @Mapping(target = "membership", ignore = true)
    @Mapping(target = "creator", ignore = true)
    @Mapping(target = "committeeIds", ignore = true)
    @Mapping(target = "inheritedRoles", ignore = true)
    @Mapping(target = "roleStrings", ignore = true)
    @Mapping(target = "authorities", ignore = true)
    @Mapping(target = "creatorId", ignore = true)
    public abstract void fromRequest(ActivationRequest request, @MappingTarget User user);

    @AfterMapping
    protected void afterFromRequest(ActivationRequest request, @MappingTarget User user) {
        if (request.getResetType() == ResetType.MEMBER_ACTIVATION) {
            user.setUsername(request.getUsername());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setEnabled(true);
        user.setResetKey(null);
        user.setResetType(null);
        user.setResetKeyValidUntil(null);
    }

    @Mapping(target = "email", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "firstName", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "prefix", ignore = true)
    @Mapping(target = "initials", ignore = true)
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "houseNumber", ignore = true)
    @Mapping(target = "postalCode", ignore = true)
    @Mapping(target = "city", ignore = true)
    @Mapping(target = "phoneNumber", ignore = true)
    @Mapping(target = "studentNumber", ignore = true)
    @Mapping(target = "dateOfBirth", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "discord", ignore = true)
    @Mapping(target = "steamid", ignore = true)
    @Mapping(target = "newsletter", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "resetKey", ignore = true)
    @Mapping(target = "resetKeyValidUntil", ignore = true)
    @Mapping(target = "resetType", ignore = true)
    @Mapping(target = "consentPrivacy", ignore = true)
    @Mapping(target = "consentGdpr", ignore = true)
    @Mapping(target = "gender", ignore = true)
    @Mapping(target = "street", ignore = true)
    @Mapping(target = "country", ignore = true)
    @Mapping(target = "photoConsent", ignore = true)
    @Mapping(target = "nationality", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "committeeMembers", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "ehbo", ignore = true)
    @Mapping(target = "contactId", ignore = true)
    @Mapping(target = "bhv", ignore = true)
    @Mapping(target = "contributions", ignore = true)
    @Mapping(target = "membership", ignore = true)
    @Mapping(target = "creator", ignore = true)
    @Mapping(target = "committeeIds", ignore = true)
    @Mapping(target = "inheritedRoles", ignore = true)
    @Mapping(target = "roleStrings", ignore = true)
    @Mapping(target = "authorities", ignore = true)
    @Mapping(target = "creatorId", ignore = true)
    public abstract void fromRequest(PasswordResetRequest request, @MappingTarget User user);

    @AfterMapping
    protected void afterFromRequest(PasswordResetRequest request, @MappingTarget User user) {
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setResetKey(null);
        user.setResetKeyValidUntil(null);
        user.setResetType(null);
    }
}
