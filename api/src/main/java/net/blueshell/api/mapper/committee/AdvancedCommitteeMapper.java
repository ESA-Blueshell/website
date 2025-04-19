package net.blueshell.api.mapper.committee;


import net.blueshell.api.dto.AdvancedCommitteeDTO;
import net.blueshell.api.mapper.CommitteeMemberMapper;
import net.blueshell.api.model.Committee;
import net.blueshell.api.model.CommitteeMember;
import net.blueshell.api.base.BaseMapper;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Mapper(
        componentModel = "spring",
        uses = {CommitteeMemberMapper.class}
)
public abstract class AdvancedCommitteeMapper extends BaseMapper<Committee, AdvancedCommitteeDTO> {

    @Autowired
    protected CommitteeMemberMapper memberMapper;

    @Mapping(target = "members", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "memberIds", ignore = true)
    @Mapping(target = "users", ignore = true)
    public abstract Committee fromDTO(AdvancedCommitteeDTO dto);

    @AfterMapping
    protected void afterFromDTO(AdvancedCommitteeDTO dto,
                                @MappingTarget Committee committee) {
        if (dto.getMembers() != null) {
            List<CommitteeMember> members = memberMapper.fromDTOs(dto.getMembers());
            members.forEach(member -> member.setCommittee(committee));
            committee.setMembers(new HashSet<>(members));
        }
    }

    @Mapping(target = "members", ignore = true)
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
