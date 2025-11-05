package net.blueshell.api.factory.dto;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.AddressDTO;
import org.springframework.stereotype.Component;

/**
 * Factory for AddressDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class AddressDTOFactory extends BaseDtoFactory<AddressDTO> {

    @Override
    public Class<AddressDTO> targetType() {
        return AddressDTO.class;
    }

    @Override
    public AddressDTO createBasic() {
        AddressDTO dto = new AddressDTO();
        dto.setCountry("NL");
        dto.setCity("Enschede");
        dto.setStreet("Test Street");
        dto.setHouseNumber("123");
        dto.setZipCode("1234 AB");
        dto.setCreatedAt(now());
        return dto;
    }
}
