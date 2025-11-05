package net.blueshell.api.factory.dto.user;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.user.SimpleUserDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for SimpleUserDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class SimpleUserDTOFactory extends BaseDtoFactory<SimpleUserDTO> {

    @Override
    public Class<SimpleUserDTO> targetType() {
        return SimpleUserDTO.class;
    }

    @Override
    public SimpleUserDTO createBasic() {
        SimpleUserDTO dto = new SimpleUserDTO();
        dto.setInitials("TU");
        dto.setFirstName("Test");
        dto.setLastName("User");
        dto.setPrefix(null);
        dto.setUsername(unique("user"));
        dto.setNewsletter(false);
        dto.setDiscord("testuser");
        dto.setEmail(email("user"));
        dto.setPhoneNumber("+31612345678");
        dto.setPassword("Password123!");
        return dto;
    }
}
