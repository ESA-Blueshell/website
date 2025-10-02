package net.blueshell.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.dto.user.SimpleUserDTO;
import net.blueshell.api.model.survey.Answer;
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
    private List<Answer> answers;

    private GuestDTO guest;

    private Long userId;
}
