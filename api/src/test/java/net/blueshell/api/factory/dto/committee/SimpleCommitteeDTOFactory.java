package net.blueshell.api.factory.dto.committee;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.committee.SimpleCommitteeDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for SimpleCommitteeDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class SimpleCommitteeDTOFactory extends BaseDtoFactory<SimpleCommitteeDTO> {

    @Override
    public Class<SimpleCommitteeDTO> targetType() {
        return SimpleCommitteeDTO.class;
    }

    @Override
    public SimpleCommitteeDTO createBasic() {
        SimpleCommitteeDTO dto = new SimpleCommitteeDTO();
        dto.setName(unique("Committee"));
        dto.setDescription("Test committee description");
        return dto;
    }

    public SimpleCommitteeDTO createWithName(String name) {
        SimpleCommitteeDTO dto = createBasic();
        dto.setName(name);
        return dto;
    }

    public SimpleCommitteeDTO createWithDetails(String name, String description) {
        SimpleCommitteeDTO dto = createBasic();
        dto.setName(name);
        dto.setDescription(description);
        return dto;
    }
}
