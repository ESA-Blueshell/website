package net.blueshell.api.mapper.committee;


import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.committee.SimpleCommitteeDTO;
import net.blueshell.api.mapper.CommitteeMemberMapper;
import net.blueshell.api.model.committee.Committee;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        uses = {CommitteeMemberMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public abstract class SimpleCommitteeMapper extends BaseMapper<Committee, SimpleCommitteeDTO> {
    @Mapping(target = "name")
    @Mapping(target = "description")
    @BeanMapping(ignoreByDefault = true)
    public abstract SimpleCommitteeDTO toDTO(Committee committee);
}
