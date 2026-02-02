package net.blueshell.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "Guest")
public class GuestDTO extends PersonalInfoDTO {
    private Long id;
    @NotNull
    private String name;
    private String accessToken;
}
