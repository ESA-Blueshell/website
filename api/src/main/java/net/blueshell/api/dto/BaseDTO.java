package net.blueshell.api.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = BlogDTO.class, name = "BlogDTO"),
        @JsonSubTypes.Type(value = EmailDTO.class, name = "EmailDTO"),
        @JsonSubTypes.Type(value = EventDTO.class, name = "EventDTO"),
        @JsonSubTypes.Type(value = SocialDTO.class, name = "SocialDTO"),
})
public abstract class BaseDTO implements Serializable {
}
