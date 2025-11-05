package net.blueshell.api.validation;

import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.validation.file.AllowedContentTypes;
import net.blueshell.api.validation.file.AllowedContentTypesValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AllowedContentTypesValidator.
 */
@SpringBootTest
class AllowedContentTypesValidatorTest {

    private AllowedContentTypesValidator validator;
    private ConstraintValidatorContext context;
    private AllowedContentTypes constraint;

    @BeforeEach
    void setUp() {
        validator = new AllowedContentTypesValidator();
        context = mock(ConstraintValidatorContext.class);
        constraint = mock(AllowedContentTypes.class);
    }

    @Test
    void validContentType() {
        when(constraint.value()).thenReturn(new String[]{"image/jpeg", "image/png"});
        validator.initialize(constraint);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/jpeg");

        assertTrue(validator.isValid(file, context));
    }

    @Test
    void invalidContentType() {
        when(constraint.value()).thenReturn(new String[]{"image/jpeg", "image/png"});
        validator.initialize(constraint);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("application/pdf");

        assertFalse(validator.isValid(file, context));
    }

    @Test
    void wildcardContentType() {
        when(constraint.value()).thenReturn(new String[]{"image/*"});
        validator.initialize(constraint);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/jpeg");

        assertTrue(validator.isValid(file, context));
    }

    @Test
    void nullContentType() {
        when(constraint.value()).thenReturn(new String[]{"image/jpeg"});
        validator.initialize(constraint);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn(null);

        assertFalse(validator.isValid(file, context));
    }

    @Test
    void emptyContentType() {
        when(constraint.value()).thenReturn(new String[]{"image/jpeg"});
        validator.initialize(constraint);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("");

        assertFalse(validator.isValid(file, context));
    }

    @Test
    void emptyFileIsValid() {
        when(constraint.value()).thenReturn(new String[]{"image/jpeg"});
        validator.initialize(constraint);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertTrue(validator.isValid(file, context));
    }

    @Test
    void nullFileIsValid() {
        when(constraint.value()).thenReturn(new String[]{"image/jpeg"});
        validator.initialize(constraint);

        assertTrue(validator.isValid(null, context));
    }
}
