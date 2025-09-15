package net.blueshell.api.validation.request;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.dto.request.MemberActivationRequest;
import net.blueshell.api.dto.request.PasswordResetRequest;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Component
public class ValidPasswordResetRequestValidator implements ConstraintValidator<ValidPasswordResetRequest, PasswordResetRequest> {

    private final UserRepository userRepository;

    public ValidPasswordResetRequestValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean isValid(PasswordResetRequest request, ConstraintValidatorContext context) {
        // 1) If username or token is missing, let @NotBlank handle it.
        if (!StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getToken())) {
            return true;
        }

        var user = userRepository.findByUsername(request.getUsername()).orElse(null);
        if (user == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Unknown username.")
                    .addPropertyNode("username")
                    .addConstraintViolation();
            return false;
        }

        if (user.getResetType() != ResetType.PASSWORD_RESET) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid reset type.")
                    .addPropertyNode("resetType")
                    .addConstraintViolation();
            return false;
        }

        if (!Objects.equals(user.getResetKey(), request.getToken())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid token.")
                    .addPropertyNode("token")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}
