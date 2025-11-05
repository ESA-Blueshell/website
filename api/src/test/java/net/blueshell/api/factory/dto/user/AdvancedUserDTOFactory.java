package net.blueshell.api.factory.dto.user;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.user.AdvancedUserDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Set;

/**
 * Factory for AdvancedUserDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class AdvancedUserDTOFactory extends BaseDtoFactory<AdvancedUserDTO> {

    @Override
    public Class<AdvancedUserDTO> targetType() {
        return AdvancedUserDTO.class;
    }

    @Override
    public AdvancedUserDTO createBasic() {
        AdvancedUserDTO dto = new AdvancedUserDTO();
        dto.setInitials("TU");
        dto.setFirstName("Test");
        dto.setLastName("User");
        dto.setUsername(unique("user"));
        dto.setNewsletter(false);
        dto.setDiscord("testuser");
        dto.setEmail(email("user"));
        dto.setPhoneNumber("+31612345678");
        dto.setPassword("Password123!");
        dto.setRoles(Set.of(Role.MEMBER));
        dto.setDateOfBirth(Date.valueOf(LocalDate.of(1990, 1, 1)));
        dto.setNationality("Dutch");
        dto.setPhotoConsent(true);
        dto.setEhbo(false);
        dto.setBhv(false);
        dto.setEnabled(true);
        dto.setGender("Male");
        return dto;
    }
}
