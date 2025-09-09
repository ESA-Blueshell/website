package net.blueshell.api.validation.user;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.dto.user.AdvancedUserDTO;
import net.blueshell.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class UniqueUserValidator implements ConstraintValidator<UniqueUser, AdvancedUserDTO> {

    private final UserRepository userRepository;

    @Autowired
    public UniqueUserValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean isValid(AdvancedUserDTO dto, ConstraintValidatorContext context) {
        if (dto == null) {
            // Let @NotNull handle this
            return true;
        }

        boolean isValid = true;

        // Retrieve the current user ID for updates
        Long currentUserId = dto.getId();

        // Validate Username
        if (StringUtils.hasText(dto.getUsername())) {
            if (currentUserId == null) { // Creation
                if (userRepository.existsByUsername(dto.getUsername())) {
                    isValid = false;
                    context.buildConstraintViolationWithTemplate("Username is already taken.")
                            .addPropertyNode("username")
                            .addConstraintViolation();
                }
            } else { // Update
                if (userRepository.existsByUsernameAndIdNot(dto.getUsername(), currentUserId)) {
                    isValid = false;
                    context.buildConstraintViolationWithTemplate("Username is already taken.")
                            .addPropertyNode("username")
                            .addConstraintViolation();
                }
            }
        }

        // Validate Email
        if (StringUtils.hasText(dto.getEmail())) {
            if (currentUserId == null) { // Creation
                if (userRepository.existsByEmail(dto.getEmail())) {
                    isValid = false;
                    context.buildConstraintViolationWithTemplate("Email is already registered.")
                            .addPropertyNode("email")
                            .addConstraintViolation();
                }
            } else { // Update
                if (userRepository.existsByEmailAndIdNot(dto.getEmail(), currentUserId)) {
                    isValid = false;
                    context.buildConstraintViolationWithTemplate("Email is already registered.")
                            .addPropertyNode("email")
                            .addConstraintViolation();
                }
            }
        }

        // Validate Phone Number
        if (StringUtils.hasText(dto.getPhoneNumber())) {
            if (currentUserId == null) { // Creation
                if (userRepository.existsByPhoneNumber(dto.getPhoneNumber())) {
                    isValid = false;
                    context.buildConstraintViolationWithTemplate("Phone number is already in use.")
                            .addPropertyNode("phoneNumber")
                            .addConstraintViolation();
                }
            } else { // Update
                if (userRepository.existsByPhoneNumberAndIdNot(dto.getPhoneNumber(), currentUserId)) {
                    isValid = false;
                    context.buildConstraintViolationWithTemplate("Phone number is already in use.")
                            .addPropertyNode("phoneNumber")
                            .addConstraintViolation();
                }
            }
        }

        if (!isValid) {
            context.disableDefaultConstraintViolation();
        }

        return isValid;
    }
}
