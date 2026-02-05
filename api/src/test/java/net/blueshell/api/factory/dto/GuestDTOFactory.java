package net.blueshell.api.factory.dto;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.GuestDTO;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Factory for GuestDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class GuestDTOFactory extends BaseDtoFactory<GuestDTO> {

    @Override
    public Class<GuestDTO> targetType() {
        return GuestDTO.class;
    }

    @Override
    public GuestDTO createBasic() {
        GuestDTO dto = new GuestDTO();
        dto.setCreatedAt(now());
        dto.setName("Guest " + nextId());
        dto.setDiscord("guest");
        dto.setEmail(email("guest"));
        dto.setPhoneNumber("+31651319571");
        dto.setAccessToken(unique("acct"));
        return dto;
    }
}
