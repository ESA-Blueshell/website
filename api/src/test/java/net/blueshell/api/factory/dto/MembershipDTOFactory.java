package net.blueshell.api.factory.dto;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.MemberType;
import net.blueshell.api.dto.MembershipDTO;
import org.springframework.stereotype.Component;

/**
 * Factory for MembershipDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class MembershipDTOFactory extends BaseDtoFactory<MembershipDTO> {

    @Override
    public Class<MembershipDTO> targetType() {
        return MembershipDTO.class;
    }

    @Override
    public MembershipDTO createBasic() {
        MembershipDTO dto = new MembershipDTO();
        dto.setUserId(nextId());
        dto.setMemberType(MemberType.REGULAR);
        dto.setCity("Enschede");
        dto.setCountry("NL");
        dto.setStartDate(today());
        dto.setEndDate(null);
        dto.setIncasso(true);
        return dto;
    }
}
