package net.blueshell.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.Email;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class SimpleUserDTO extends BaseDTO {

    @JsonProperty
    private Long id;

    @JsonProperty
    private String username;

    @JsonProperty
    private String discord;

    @JsonProperty
    private String firstName;

    @JsonProperty
    private String prefix;

    @JsonProperty
    private String lastName;

    @JsonProperty
    private String fullName;

    @JsonProperty
    @Email
    private String email;
}
