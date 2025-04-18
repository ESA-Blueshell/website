package net.blueshell.common.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import net.blueshell.common.dto.event.EventDTO;

import java.io.Serializable;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = InternalBlogDTO.class, name = "BlogDTO"),
        @JsonSubTypes.Type(value = EmailDTO.class, name = "EmailDTO"),
        @JsonSubTypes.Type(value = EventDTO.class, name = "EventDTO"),
        @JsonSubTypes.Type(value = SocialDTO.class, name = "SocialDTO"),
})
public abstract class BaseDTO implements Serializable {
}
