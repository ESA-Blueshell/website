package net.blueshell.api.validation.file;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AllowedContentTypesValidator implements ConstraintValidator<AllowedContentTypes, MultipartFile> {

    private List<MediaType> allowed;

    @Override
    public void initialize(AllowedContentTypes constraintAnnotation) {
        this.allowed = Arrays.stream(constraintAnnotation.value())
                .map(MediaType::parseMediaType)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
        // Let @NotNull/@NotEmpty handle requiredness
        if (file == null || file.isEmpty()) {
            return true;
        }

        String ct = file.getContentType();
        if (ct == null || ct.isBlank()) {
            return false;
        }

        try {
            MediaType actual = MediaType.parseMediaType(ct);
            return allowed.stream().anyMatch(a -> a.includes(actual));
        } catch (IllegalArgumentException e) {
            return false; // Invalid media type format
        }
    }
}