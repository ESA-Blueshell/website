package net.blueshell.api.mapper.committee;


import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Mapper(
        componentModel = "spring",
        uses = {CommitteeMemberMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public abstract class AdvancedCommitteeMapper extends BaseMapper<Committee, AdvancedCommitteeDTO> {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name")
    @Mapping(target = "description")
    @Mapping(target = "members")
    public abstract Committee fromDTO(AdvancedCommitteeDTO dto, @MappingTarget Committee committee);

    @AfterMapping
    protected void wireBackRefs(AdvancedCommitteeDTO dto, @MappingTarget Committee committee) {
        if (committee.getMembers() != null) {
            log.info("Committee members map: {}", committee.getMembers());
            log.info("Committee members map dto: {}", dto.getMembers());
            for (CommitteeMember m : committee.getMembers()) {
                m.setCommittee(committee);
            }
        }
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "name")
    @Mapping(target = "description")
    @Mapping(target = "members")
    public abstract AdvancedCommitteeDTO toDTO(Committee committee);
}
