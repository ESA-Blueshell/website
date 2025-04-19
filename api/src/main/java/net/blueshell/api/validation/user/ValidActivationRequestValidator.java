package net.blueshell.api.validation.user;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.controller.request.ActivationRequest;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ValidActivationRequestValidator implements ConstraintValidator<ValidActivationRequest, ActivationRequest> {

    private final UserRepository userRepository;

    public ValidActivationRequestValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean isValid(ActivationRequest request, ConstraintValidatorContext context) {
        // 1) If username or token is missing, let @NotBlank handle it.
        if (!StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getToken())) {
            return true;
        }

        if (request.getResetType() != ResetType.USER_ACTIVATION && request.getResetType() != ResetType.MEMBER_ACTIVATION) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid reset type.")
                    .addPropertyNode("resetType")
                    .addConstraintViolation();
        }

        User user;
        if (request.getResetType() == ResetType.USER_ACTIVATION) {
            user = userRepository.findByUsername(request.getUsername()).orElse(null);
            if (user == null) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Unknown username.")
                        .addPropertyNode("username")
                        .addConstraintViolation();
                return false;
            }
        } else if (request.getResetType() == ResetType.MEMBER_ACTIVATION) {
            user = userRepository.findByResetKey(request.getToken()).orElse(null);
            if (user == null) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Unknown token.")
                        .addPropertyNode("token")
                        .addConstraintViolation();
                return false;
            }
        } else {
            return false;
        }

        if (user.getResetType() != request.getResetType()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid reset type.")
                    .addPropertyNode("resetType")
                    .addConstraintViolation();
            return false;
        }

        if (!request.getToken().equals(user.getResetKey())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid token.")
                    .addPropertyNode("token")
                    .addConstraintViolation();
            return false;
        }

        if (request.getResetType() == ResetType.MEMBER_ACTIVATION) {
            if (!StringUtils.hasText(request.getPassword())) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Password is required.")
                        .addPropertyNode("password")
                        .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
