package net.blueshell.api.factory.dto;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.error.ApiErrorDTO;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/**
 * Factory for ApiErrorDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class ApiErrorDTOFactory extends BaseDtoFactory<ApiErrorDTO> {

    private final FieldValidationErrorDTOFactory fieldFactory;

    @Override
    public Class<ApiErrorDTO> targetType() {
        return ApiErrorDTO.class;
    }

    @Override
    public ApiErrorDTO createBasic() {
        ApiErrorDTO dto = new ApiErrorDTO();
        dto.type = "about:blank";
        dto.title = "Bad Request";
        dto.status = 400;
        dto.detail = "Validation failed";
        dto.instance = URI.create("/api/v1/test");
        dto.errors = List.of(fieldFactory.createBasic());
        dto.traceId = unique("trace");
        return dto;
    }
}
