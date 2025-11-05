package net.blueshell.api.validation.file;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

public class FileSizeValidator implements ConstraintValidator<FileSize, MultipartFile> {

    private long min;
    private long max;
    private boolean allowEmpty;

    @Override
    public void initialize(FileSize constraintAnnotation) {
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
        this.allowEmpty = constraintAnnotation.allowEmpty();
    }

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext ctx) {
        if (file == null) return true;

        if (file.isEmpty()) {
            return allowEmpty;
        }

        long size = file.getSize();
        return size >= min && size <= max;
    }
}