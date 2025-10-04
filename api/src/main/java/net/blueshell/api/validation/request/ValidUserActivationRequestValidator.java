package net.blueshell.api.validation.request;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.dto.request.UserActivationRequest;
import net.blueshell.api.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ValidUserActivationRequestValidator implements ConstraintValidator<ValidUserActivationRequest, UserActivationRequest> {

    private final UserRepository userRepository;

    public ValidUserActivationRequestValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean isValid(UserActivationRequest request, ConstraintValidatorContext context) {
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

        if (user.getResetType() != ResetType.USER_ACTIVATION) {
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

        return true;
    }
}
