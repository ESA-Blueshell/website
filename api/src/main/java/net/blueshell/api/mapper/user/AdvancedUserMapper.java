package net.blueshell.api.mapper.user;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.user.AdvancedUserDTO;
import net.blueshell.api.model.User;
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

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "initials")
    @Mapping(target = "firstName")
    @Mapping(target = "prefix")
    @Mapping(target = "lastName")
    @Mapping(target = "username", conditionExpression = "java(hasAuthority(net.blueshell.api.common.enums.Role.BOARD))")
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "email")
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
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "creator", ignore = true)
    public abstract User fromDTO(AdvancedUserDTO dto);

    @ObjectFactory
    protected User newUser(@TargetType Class<User> type, AdvancedUserDTO dto) {
        return new User();
    }

    @AfterMapping
    protected void afterFromDTO(AdvancedUserDTO dto, @MappingTarget User user) {
        // Newly created or Board update
        if (user.getCreatedAt() == null || hasAuthority(Role.BOARD)) {
            applyCreationFields(dto, user);

            // If creating a account for the first time, set the password. Otherwise create a random one
            if (dto.getPassword() != null && !hasAuthority(Role.BOARD)) {
                user.setPassword(passwordEncoder.encode(dto.getPassword()));
            } else if (dto.getPassword() == null && dto.getId() == null && hasAuthority(Role.BOARD)) {
                user.setPassword(passwordEncoder.encode(generatePassword()));
            }

            if (hasAuthority(Role.BOARD)) {
                user.setCreatorId(getPrincipal().getId());
            }
        }
    }
}
