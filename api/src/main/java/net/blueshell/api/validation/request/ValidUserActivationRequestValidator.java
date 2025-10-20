package net.blueshell.api.validation.request;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.dto.request.UserActivationRequest;
import net.blueshell.api.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;

@Component
public class ValidUserActivationRequestValidator implements ConstraintValidator<ValidUserActivationRequest, UserActivationRequest> {

    private final UserRepository userRepository;

    public ValidUserActivationRequestValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean isValid(UserActivationRequest request, ConstraintValidatorContext context) {
        // Let @NotBlank on fields handle empty values.
        if (!StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getToken())) {
            return true;
        }

        var userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isEmpty()) {
            return reject(context, "We couldn’t verify your activation link. It may be invalid or already used.");
        }

        var user = userOpt.get();

        // Wrong flow (e.g., not an activation token)
        if (user.getResetType() != ResetType.USER_ACTIVATION) {
            return reject(context, "We couldn’t verify your activation link. It may be invalid or already used.");
        }

        // Expired token
        if (user.getResetKeyValidUntil() != null
                && user.getResetKeyValidUntil().compareTo(Instant.now()) < 0 ) {
            return reject(context, "Your activation link has expired. Please request a new activation email.");
        }

        // Token mismatch
        if (!request.getToken().equals(user.getResetKey())) {
            return reject(context, "We couldn’t verify your activation link. It may be invalid or already used.");
        }

        return true;
    }

    private boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
