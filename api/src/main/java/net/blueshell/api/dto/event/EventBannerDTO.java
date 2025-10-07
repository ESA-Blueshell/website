package net.blueshell.api.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.dto.FileDTO;
import net.blueshell.api.dto.survey.SurveyDTO;
import org.springframework.boot.Banner;

import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "EventBanner")
public class EventBannerDTO extends BaseDTO {
    private Long id;
    @NotNull
    private Long fileId;
    @NotNull
    private FileDTO file;
}
