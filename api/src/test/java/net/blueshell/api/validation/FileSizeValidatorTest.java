package net.blueshell.api.validation;

import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.validation.file.FileSize;
import net.blueshell.api.validation.file.FileSizeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FileSizeValidator.
 */
@SpringBootTest
class FileSizeValidatorTest {

    private FileSizeValidator validator;
    private ConstraintValidatorContext context;
    private FileSize constraint;

    @BeforeEach
    void setUp() {
        validator = new FileSizeValidator();
        context = mock(ConstraintValidatorContext.class);
        constraint = mock(FileSize.class);
    }

    @Test
    void validFileSizeWithinRange() {
        when(constraint.min()).thenReturn(100L);
        when(constraint.max()).thenReturn(1000L);
        when(constraint.allowEmpty()).thenReturn(false);
        validator.initialize(constraint);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(500L);

        assertTrue(validator.isValid(file, context));
    }

    @Test
    void fileSizeTooSmall() {
        when(constraint.min()).thenReturn(100L);
        when(constraint.max()).thenReturn(1000L);
        when(constraint.allowEmpty()).thenReturn(false);
        validator.initialize(constraint);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(50L);

        assertFalse(validator.isValid(file, context));
    }

    @Test
    void fileSizeTooLarge() {
        when(constraint.min()).thenReturn(100L);
        when(constraint.max()).thenReturn(1000L);
        when(constraint.allowEmpty()).thenReturn(false);
        validator.initialize(constraint);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1500L);

        assertFalse(validator.isValid(file, context));
    }

    @Test
    void emptyFileAllowed() {
        when(constraint.min()).thenReturn(100L);
        when(constraint.max()).thenReturn(1000L);
        when(constraint.allowEmpty()).thenReturn(true);
        validator.initialize(constraint);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertTrue(validator.isValid(file, context));
    }

    @Test
    void emptyFileNotAllowed() {
        when(constraint.min()).thenReturn(100L);
        when(constraint.max()).thenReturn(1000L);
        when(constraint.allowEmpty()).thenReturn(false);
        validator.initialize(constraint);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertFalse(validator.isValid(file, context));
    }

    @Test
    void nullFileIsValid() {
        when(constraint.min()).thenReturn(100L);
        when(constraint.max()).thenReturn(1000L);
        when(constraint.allowEmpty()).thenReturn(false);
        validator.initialize(constraint);

        assertTrue(validator.isValid(null, context));
    }
}
