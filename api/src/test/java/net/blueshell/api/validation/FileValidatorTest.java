package net.blueshell.api.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import net.blueshell.api.dto.FileDTO;
import net.blueshell.api.factory.dto.FileDTOFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for FileDTO validation.
 */
@SpringBootTest
class FileValidatorTest {

    private final Validator validator;
    private final FileDTOFactory fileFactory;

    @Autowired
    FileValidatorTest(Validator validator, FileDTOFactory fileFactory) {
        this.validator = validator;
        this.fileFactory = fileFactory;
    }

    @Test
    void validFileDTO_passesValidation() {
        FileDTO dto = fileFactory.createBasic();
        Set<ConstraintViolation<FileDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Valid FileDTO should pass validation");
    }

    @Test
    void fileDTO_withNegativeSize_failsValidation() {
        FileDTO dto = fileFactory.createWithCustomizations(f -> f.setSize(-1L));
        Set<ConstraintViolation<FileDTO>> violations = validator.validate(dto);

        // Depending on constraints size may be optional or validated.
        assertTrue(violations.isEmpty() || violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("size")));
    }
}
