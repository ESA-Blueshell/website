package net.blueshell.api.validation;

import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.dto.event.EventSignUpDTO;
import net.blueshell.api.factory.dto.GuestDTOFactory;
import net.blueshell.api.factory.dto.event.EventSignUpDTOFactory;
import net.blueshell.api.factory.dto.user.SimpleUserDTOFactory;
import net.blueshell.api.validation.event.GuestOrUserRequiredValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GuestOrUserRequiredValidator.
 */
@SpringBootTest
class GuestOrUserRequiredValidatorTest {

    private final GuestOrUserRequiredValidator validator = new GuestOrUserRequiredValidator();

    private final EventSignUpDTOFactory signUpFactory;
    private final GuestDTOFactory guestFactory;
    private final SimpleUserDTOFactory simpleUserFactory;

    @Autowired
    GuestOrUserRequiredValidatorTest(
            EventSignUpDTOFactory signUpFactory,
            GuestDTOFactory guestFactory,
            SimpleUserDTOFactory simpleUserFactory
    ) {
        this.signUpFactory = signUpFactory;
        this.guestFactory = guestFactory;
        this.simpleUserFactory = simpleUserFactory;
    }

    @Test
    void valid_when_guest_present() {
        EventSignUpDTO dto = signUpFactory.createWithCustomizations(es -> {
            es.setEventId(1L);
            es.setGuest(guestFactory.createWithCustomizations(g -> g.setName("Guesty McGuestface")));
            es.setUser(null);
            es.setUserId(null);
        });

        assertTrue(validator.isValid(dto, mock(ConstraintValidatorContext.class)));
    }

    @Test
    void valid_when_user_present() {
        EventSignUpDTO dto = signUpFactory.createWithCustomizations(es -> {
            es.setEventId(1L);
            es.setUser(simpleUserFactory.createWithCustomizations(u -> u.setId(42L)));
            es.setUserId(null);
            es.setGuest(null);
        });

        assertTrue(validator.isValid(dto, mock(ConstraintValidatorContext.class)));
    }

    @Test
    void valid_when_userId_present() {
        EventSignUpDTO dto = signUpFactory.createWithCustomizations(es -> {
            es.setEventId(1L);
            es.setUser(null);
            es.setGuest(null);
            es.setUserId(99L);
        });

        assertTrue(validator.isValid(dto, mock(ConstraintValidatorContext.class)));
    }

    @Test
    void invalid_when_neither_guest_nor_user_provided() {
        EventSignUpDTO dto = signUpFactory.createWithCustomizations(es -> {
            es.setEventId(1L);
            es.setGuest(null);
            es.setUser(null);
            es.setUserId(null);
        });

        var ctx = mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);

        assertFalse(validator.isValid(dto, ctx));
        verify(ctx).disableDefaultConstraintViolation();
        verify(ctx).buildConstraintViolationWithTemplate("Either guest or user (or userId) must be provided.");
    }
}
