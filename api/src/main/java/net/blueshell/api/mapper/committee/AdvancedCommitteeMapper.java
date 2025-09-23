package net.blueshell.api.mapper.committee;


import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.committee.AdvancedCommitteeDTO;
import net.blueshell.api.mapper.CommitteeMemberMapper;
import net.blueshell.api.model.Committee;
import net.blueshell.api.model.CommitteeMember;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(
        componentModel = "spring",
        uses = {CommitteeMemberMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public abstract class AdvancedCommitteeMapper extends BaseMapper<Committee, AdvancedCommitteeDTO> {

    @Autowired
    protected CommitteeMemberMapper memberMapper;

    @BeanMapping(ignoreByDefault = true)
    public abstract Committee fromDTO(AdvancedCommitteeDTO dto, @MappingTarget Committee committee);

    @AfterMapping
    protected void afterFromDTO(AdvancedCommitteeDTO dto, @MappingTarget Committee committee) {

        if (dto.getMembers() == null) {
            committee.setMembers(new HashSet<>());
            return;
        }

        Set<CommitteeMember> members = memberMapper.fromDTOs(dto.getMembers())
                .stream()
                .peek(cm -> cm.setCommittee(committee))
                .collect(Collectors.toSet());

        committee.setMembers(members);
    }

    @BeanMapping(ignoreByDefault = true)
    public abstract AdvancedCommitteeDTO toDTO(Committee committee);

    @AfterMapping
    protected void afterToDTO(Committee committee,
                              @MappingTarget AdvancedCommitteeDTO dto) {
        if (committee.getMembers() != null) {
            dto.setMembers(
                    memberMapper.toDTOs(new ArrayList<>(committee.getMembers()))
            );
        }
    }
}
