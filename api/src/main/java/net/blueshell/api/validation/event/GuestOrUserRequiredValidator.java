package net.blueshell.api.validation.event;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.dto.event.EventSignUpDTO;

public class GuestOrUserRequiredValidator implements ConstraintValidator<GuestOrUserRequired, EventSignUpDTO> {

    @Override
    public boolean isValid(EventSignUpDTO dto, ConstraintValidatorContext ctx) {
        if (dto == null) return true;

        final boolean hasGuest = dto.getGuest() != null;
        final boolean hasUser = dto.getUser() != null || dto.getUserId() != null;

        if (hasGuest || hasUser) return true;

        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate("Either guest or user (or userId) must be provided.")
                .addPropertyNode("guest")
                .addConstraintViolation();
        return false;
    }
}
