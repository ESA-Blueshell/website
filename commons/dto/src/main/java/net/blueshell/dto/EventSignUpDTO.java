package net.blueshell.dto;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;


@Data
@EqualsAndHashCode(callSuper = false)
public class EventSignUpDTO extends BaseDTO {
    private Long id;
    private Long eventId;
    private String fullName;
    private String discord;
    private String email;
    @Valid
    private List<Object> formAnswers;
}
