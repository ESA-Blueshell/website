package net.blueshell.api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.Email;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.DTO;

@Data
@EqualsAndHashCode(callSuper = false)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SimpleUserDTO.class, name = "simple"),
        @JsonSubTypes.Type(value = AdvancedUserDTO.class, name = "advanced"),
})
public class SimpleUserDTO extends DTO {

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
