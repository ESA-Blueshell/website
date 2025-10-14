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
    public abstract Committee fromDTO(AdvancedCommitteeDTO dto, @MappingTarget Committee committee);

    @AfterMapping
    protected void wireBackRefs(AdvancedCommitteeDTO dto, @MappingTarget Committee committee) {
        if (committee.getMembers() != null) {
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
