package net.blueshell.api.validation.request;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.dto.request.MemberActivationRequest;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ValidMemberActivationRequestValidator implements ConstraintValidator<ValidMemberActivationRequest, MemberActivationRequest> {

    private final UserRepository userRepository;

    public ValidMemberActivationRequestValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean isValid(MemberActivationRequest request, ConstraintValidatorContext context) {
        // 1) If username or token is missing, let @NotBlank handle it.
        if (!StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getToken())) {
            return true;
        }

        var user = userRepository.findByResetKey(request.getToken()).orElse(null);
        if (user == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Unknown token.")
                    .addPropertyNode("token")
                    .addConstraintViolation();
            return false;
        }

        if (user.getResetType() != ResetType.MEMBER_ACTIVATION) {
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
