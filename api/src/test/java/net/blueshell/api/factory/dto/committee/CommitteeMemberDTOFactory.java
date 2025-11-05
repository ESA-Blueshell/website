package net.blueshell.api.factory.dto.committee;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.committee.CommitteeMemberDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import net.blueshell.api.factory.dto.user.SimpleUserDTOFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for CommitteeMemberDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class CommitteeMemberDTOFactory extends BaseDtoFactory<CommitteeMemberDTO> {

    private final SimpleUserDTOFactory userFactory;

    @Override
    public Class<CommitteeMemberDTO> targetType() {
        return CommitteeMemberDTO.class;
    }

    @Override
    public CommitteeMemberDTO createBasic() {
        CommitteeMemberDTO dto = new CommitteeMemberDTO();
        dto.setUserId(nextId());
        dto.setCommitteeId(nextId());
        dto.setRole("Member");
        return dto;
    }

    public CommitteeMemberDTO createWithRole(String role) {
        CommitteeMemberDTO dto = createBasic();
        dto.setRole(role);
        return dto;
    }

    public CommitteeMemberDTO createWithIds(Long userId, Long committeeId) {
        CommitteeMemberDTO dto = createBasic();
        dto.setUserId(userId);
        dto.setCommitteeId(committeeId);
        return dto;
    }

    public CommitteeMemberDTO createChair() { return createWithRole("Chair"); }
    public CommitteeMemberDTO createSecretary() { return createWithRole("Secretary"); }
    public CommitteeMemberDTO createTreasurer() { return createWithRole("Treasurer"); }
    public CommitteeMemberDTO createRegularMember() { return createWithRole("Member"); }
}
