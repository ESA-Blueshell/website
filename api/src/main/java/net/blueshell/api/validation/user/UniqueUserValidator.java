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

    private final UserRepository userRepository;

    @Autowired
    public UniqueUserValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean isValid(SimpleUserDTO dto, ConstraintValidatorContext context) {
        if (dto == null) return true;

        boolean isValid = true;
        Long currentUserId = dto.getId();

        BiConsumer<String, String> addViolation = (property, message) -> {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode(property)
                    .addConstraintViolation();
        };

        if (StringUtils.hasText(dto.getUsername())) {
            boolean taken = (currentUserId == null)
                    ? userRepository.existsByUsername(dto.getUsername())
                    : userRepository.existsByUsernameAndIdNot(dto.getUsername(), currentUserId);
            if (taken) {
                isValid = false;
                addViolation.accept("username", "Username is taken.");
            }
        }

        if (StringUtils.hasText(dto.getEmail())) {
            boolean taken = (currentUserId == null)
                    ? userRepository.existsByEmail(dto.getEmail())
                    : userRepository.existsByEmailAndIdNot(dto.getEmail(), currentUserId);
            if (taken) {
                isValid = false;
                addViolation.accept("email", "Email is taken.");
            }
        }

        if (StringUtils.hasText(dto.getDiscord())) {
            boolean taken = (currentUserId == null)
                    ? userRepository.existsByDiscord(dto.getDiscord())
                    : userRepository.existsByDiscordAndIdNot(dto.getDiscord(), currentUserId);
            if (taken) {
                isValid = false;
                addViolation.accept("discord", "Discord is taken.");
            }
        }

        if (dto instanceof AdvancedUserDTO adv && StringUtils.hasText(adv.getPhoneNumber())) {
            boolean taken = (currentUserId == null)
                    ? userRepository.existsByPhoneNumber(adv.getPhoneNumber())
                    : userRepository.existsByPhoneNumberAndIdNot(adv.getPhoneNumber(), currentUserId);
            if (taken) {
                isValid = false;
                addViolation.accept("phoneNumber", "Phone number is taken.");
            }
        }

        return isValid;
    }
}
