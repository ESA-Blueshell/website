package net.blueshell.api.validation.membership;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.service.MembershipService;
import net.blueshell.api.service.UserService;
import net.blueshell.api.validation.user.UniqueUsername;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Validator to check if the username is unique.
 */
@Component
@Slf4j
public class NoExistingMembershipForUserIdValidator implements ConstraintValidator<NoExistingMembershipForUserId, Long> {

    private final MembershipService members;

    @Autowired
    public NoExistingMembershipForUserIdValidator(MembershipService members) {
        this.members = members;
    }

    @Override
    public boolean isValid(Long userId, ConstraintValidatorContext context) {
        log.warn("Should be called!!!!!!!!!");
        return !members.existsByUserId(userId);
    }
}
