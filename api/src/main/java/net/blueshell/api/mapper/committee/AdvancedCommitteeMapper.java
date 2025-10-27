package net.blueshell.api.mapper.committee;


import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.committee.AdvancedCommitteeDTO;
import net.blueshell.api.model.committee.Committee;
import net.blueshell.api.model.committee.CommitteeMember;
import org.mapstruct.*;

@Slf4j
@Mapper(
        componentModel = "spring",
        uses = {CommitteeMemberMapper.class}
)
public abstract class AdvancedCommitteeMapper extends BaseMapper<Committee, AdvancedCommitteeDTO> {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name")
    @Mapping(target = "description")
    @Mapping(target = "members")
    @Mapping(target = "version")
    public abstract Committee fromDTO(AdvancedCommitteeDTO dto, @MappingTarget Committee committee);

    @AfterMapping
    protected void wireBackRefs(AdvancedCommitteeDTO dto, @MappingTarget Committee committee) {
        log.info("Wiring back refs for committee {}", committee);
        log.info("dto: {}", dto);
        if (committee.getMembers() != null) {
            log.info("Wiring members for committee {}", committee.getMembers());
            for (CommitteeMember m : committee.getMembers()) {
                log.info("Member of committee member: {}", m);
                m.setCommittee(committee);
            }
        }
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "name")
    @Mapping(target = "description")
    @Mapping(target = "members")
    @Mapping(target = "version")
    public abstract AdvancedCommitteeDTO toDTO(Committee committee);
}
