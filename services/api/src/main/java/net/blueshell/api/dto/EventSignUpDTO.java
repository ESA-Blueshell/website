package net.blueshell.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.DTO;
import net.blueshell.api.model.FormAnswer;

import java.util.List;


@Data
@EqualsAndHashCode(callSuper = false)
public class EventSignUpDTO extends DTO {
    private Long id;
    private Long eventId;
    private String fullName;
    private String discord;
    private String email;
    @Valid
    private List<Object> formAnswers;
}
