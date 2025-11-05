package net.blueshell.api.factory.dto.committee;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.committee.AdvancedCommitteeDTO;
import net.blueshell.api.dto.committee.CommitteeMemberDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Factory for AdvancedCommitteeDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class AdvancedCommitteeDTOFactory extends BaseDtoFactory<AdvancedCommitteeDTO> {

    private final CommitteeMemberDTOFactory memberFactory;

    @Override
    public Class<AdvancedCommitteeDTO> targetType() {
        return AdvancedCommitteeDTO.class;
    }

    @Override
    public AdvancedCommitteeDTO createBasic() {
        return createWithMemberCount(1);
    }

    @Override
    public AdvancedCommitteeDTO createFull() {
        return createWithMemberCount(3);
    }

    /** Create with a specific number of members. */
    public AdvancedCommitteeDTO createWithMemberCount(int memberCount) {
        if (memberCount < 1) {
            throw new IllegalArgumentException("Member count must be at least 1");
        }

        AdvancedCommitteeDTO dto = new AdvancedCommitteeDTO();
        dto.setName(unique("Committee"));
        dto.setDescription("Test committee description");

        List<CommitteeMemberDTO> members = IntStream.range(0, memberCount)
                .mapToObj(i -> {
                    CommitteeMemberDTO member = memberFactory.createBasic();
                    if (memberCount > 1) {
                        String role = getRoleForIndex(i, memberCount);
                        member.setRole(role);
                    }
                    return member;
                })
                .collect(Collectors.toList());

        dto.setMembers(members);
        return dto;
    }

    /** Create with explicit member roles. */
    public AdvancedCommitteeDTO createWithMemberRoles(String... roles) {
        AdvancedCommitteeDTO dto = new AdvancedCommitteeDTO();
        dto.setName(unique("Committee"));
        dto.setDescription("Test committee description");

        List<CommitteeMemberDTO> members = IntStream.range(0, roles.length)
                .mapToObj(i -> {
                    CommitteeMemberDTO member = memberFactory.createBasic();
                    member.setRole(roles[i]);
                    return member;
                })
                .collect(Collectors.toList());

        dto.setMembers(members);
        return dto;
    }

    private String getRoleForIndex(int index, int totalMembers) {
        if (totalMembers == 1) return "Chair";
        return switch (index) {
            case 0 -> "Chair";
            case 1 -> "Secretary";
            case 2 -> "Treasurer";
            default -> "Member";
        };
    }

    // Convenience
    public AdvancedCommitteeDTO createWithSingleMember() {
        return createWithMemberCount(1);
    }

    public AdvancedCommitteeDTO createWithStandardBoard() {
        return createWithMemberCount(3);
    }

    public AdvancedCommitteeDTO createWithLargeCommittee() {
        return createWithMemberCount(7);
    }

    public AdvancedCommitteeDTO createWithCustomMembers(List<CommitteeMemberDTO> customMembers) {
        AdvancedCommitteeDTO dto = new AdvancedCommitteeDTO();
        dto.setName(unique("Committee"));
        dto.setDescription("Test committee description");
        dto.setMembers(customMembers);
        return dto;
    }
}
