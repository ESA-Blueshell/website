package net.blueshell.api.mapper.user;

import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.AdvancedUserDTO;
import net.blueshell.api.mapper.MembershipMapper;
import net.blueshell.api.model.Membership;
import net.blueshell.api.model.User;
import net.blueshell.api.service.MembershipService;
import net.blueshell.api.base.BaseMapper;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.function.BiConsumer;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class AdvancedUserMapper extends BaseMapper<User, AdvancedUserDTO> {

    private static final String CHAR_SET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&*()-_=+<>?";
    private static final int PASSWORD_LENGTH = 12;
    private static final SecureRandom random = new SecureRandom();

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MembershipMapper membershipMapper;
    @Autowired
    private MembershipService membershipService;

    public static void applyCreationFields(AdvancedUserDTO dto, User user) {
        applyIfFieldIsNotNull(user, dto.getInitials(), User::setInitials);
        applyIfFieldIsNotNull(user, dto.getFirstName(), User::setFirstName);
        applyIfFieldIsNotNull(user, dto.getPrefix(), User::setPrefix);
        applyIfFieldIsNotNull(user, dto.getLastName(), User::setLastName);
        applyIfFieldIsNotNull(user, dto.getEmail(), User::setEmail);
    }

    private static <T> void applyIfFieldIsNotNull(User user, T obj, BiConsumer<User, T> applier) {
        if (obj != null) {
            applier.accept(user, obj);
        }
    }

    public static String generatePassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            int index = random.nextInt(CHAR_SET.length());
            password.append(CHAR_SET.charAt(index));
        }
        return password.toString();
    }

    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "roles", expression = "java(user.getInheritedRoles())")
    public abstract AdvancedUserDTO toDTO(User user);

    @AfterMapping
    protected void afterToDto(User user, @MappingTarget AdvancedUserDTO dto) {
        if (user.getMembership() != null) {
            dto.setMembership(membershipMapper.toDTO(user.getMembership()));
        }
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "initials", ignore = true)
    @Mapping(target = "firstName", ignore = true)
    @Mapping(target = "prefix", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "username", conditionExpression = "java(hasAuthority(net.blueshell.api.common.enums.Role.BOARD))")
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "committeeMembers", ignore = true)
    @Mapping(target = "contributions", ignore = true)
    @Mapping(target = "resetKey", ignore = true)
    @Mapping(target = "resetKeyValidUntil", ignore = true)
    @Mapping(target = "resetType", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "password", ignore = true)
    public abstract User fromDTO(AdvancedUserDTO dto);

    @AfterMapping
    protected void afterFromDTO(AdvancedUserDTO dto, @MappingTarget User user) {
        // Newly created or Board update
        if (user.getCreatedAt() == null || hasAuthority(Role.BOARD)) {
            applyCreationFields(dto, user);

            // If creating a account for the first time, set the password. Otherwise create a random one
            if (dto.getPassword() != null && !hasAuthority(Role.BOARD)) {
                user.setPassword(passwordEncoder.encode(dto.getPassword()));
            } else {
                user.setPassword(passwordEncoder.encode(generatePassword()));
            }

            if (hasAuthority(Role.BOARD)) {
                user.setCreatorId(getPrincipal().getId());
            }

            if (dto.getMembership() != null && user.getMembership() != null) {
                dto.setId(user.getMembership().getId());
                Membership membership = membershipMapper.fromDTO(dto.getMembership());
                membership.setUser(user);
                membershipService.update(membership);
                user.setMembership(membership);
            }

            if (dto.getMembership() != null && user.getMembership() == null) {
                Membership membership = membershipMapper.fromDTO(dto.getMembership());
                membership.setUser(user);
                membershipService.create(membership);
                user.addRole(Role.MEMBER);
                user.setConsentPrivacy(true);
            }
        }
    }
}
