package net.blueshell.api.factory.dto;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.error.FieldValidationErrorDTO;
import org.springframework.stereotype.Component;

/**
 * Factory for FieldValidationErrorDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class FieldValidationErrorDTOFactory extends BaseDtoFactory<FieldValidationErrorDTO> {

    @Override
    public Class<FieldValidationErrorDTO> targetType() {
        return FieldValidationErrorDTO.class;
    }

    @Override
    public FieldValidationErrorDTO createBasic() {
        FieldValidationErrorDTO e = new FieldValidationErrorDTO();
        e.objectName = "requestBody";
        e.field = "email";
        e.rejectedValue = "not-an-email";
        e.message = "must be a well-formed email address";
        e.code = "Email";
        return e;
    }
}
