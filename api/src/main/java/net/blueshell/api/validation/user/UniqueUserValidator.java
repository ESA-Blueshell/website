package net.blueshell.api.validation.user;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.dto.user.AdvancedUserDTO;
import net.blueshell.api.dto.user.SimpleUserDTO;
import net.blueshell.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.BiConsumer;

@Component
public class UniqueUserValidator implements ConstraintValidator<UniqueUser, SimpleUserDTO> {

    private final UserRepository users;

    @Autowired
    public UniqueUserValidator(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean isValid(SimpleUserDTO dto, ConstraintValidatorContext context) {
        if (dto == null) return true;

        boolean isValid = true;
        var currentUserId = dto.getId();

        BiConsumer<String, String> addViolation = (property, message) -> {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode(property)
                    .addConstraintViolation();
        };

        if (StringUtils.hasText(dto.getUsername())) {
            boolean taken = (currentUserId == null)
                    ? users.existsByUsername(dto.getUsername())
                    : users.existsByUsernameAndIdNot(dto.getUsername(), currentUserId);
            if (taken) {
                isValid = false;
                addViolation.accept("username", "Username is taken.");
            }
        }

        if (StringUtils.hasText(dto.getEmail())) {
            boolean taken = (currentUserId == null)
                    ? users.existsByEmail(dto.getEmail())
                    : users.existsByEmailAndIdNot(dto.getEmail(), currentUserId);
            if (taken) {
                isValid = false;
                addViolation.accept("email", "Email is taken.");
            }
        }

        if (StringUtils.hasText(dto.getDiscord())) {
            boolean taken = (currentUserId == null)
                    ? users.existsByDiscord(dto.getDiscord())
                    : users.existsByDiscordAndIdNot(dto.getDiscord(), currentUserId);
            if (taken) {
                isValid = false;
                addViolation.accept("discord", "Discord is taken.");
            }
        }

        if (dto instanceof AdvancedUserDTO adv && StringUtils.hasText(adv.getPhoneNumber())) {
            boolean taken = (currentUserId == null)
                    ? users.existsByPhoneNumber(adv.getPhoneNumber())
                    : users.existsByPhoneNumberAndIdNot(adv.getPhoneNumber(), currentUserId);
            if (taken) {
                isValid = false;
                addViolation.accept("phoneNumber", "Phone number is taken.");
            }
        }

        return isValid;
    }
}
