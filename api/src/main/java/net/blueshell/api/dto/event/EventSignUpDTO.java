package net.blueshell.api.dto.event;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.dto.GuestDTO;
import net.blueshell.api.dto.survey.AnswerDTO;
import org.jetbrains.annotations.NotNull;

import java.util.List;


@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "EventSignUp")
public class EventSignUpDTO extends BaseDTO {
    private Long id;

    @NotNull
    private Long eventId;

    @Valid
    private List<AnswerDTO> answers;

    private GuestDTO guest;

    private Long userId;
}
