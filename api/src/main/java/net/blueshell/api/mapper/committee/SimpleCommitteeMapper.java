package net.blueshell.api.mapper.committee;


import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.committee.SimpleCommitteeDTO;
import net.blueshell.api.mapper.CommitteeMemberMapper;
import net.blueshell.api.model.Committee;
import net.blueshell.api.repository.CommitteeMemberRepository;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
        componentModel = "spring",
        uses = {CommitteeMemberMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public abstract class SimpleCommitteeMapper extends BaseMapper<Committee, SimpleCommitteeDTO> {

    @BeanMapping(ignoreByDefault = true)
    public abstract SimpleCommitteeDTO toDTO(Committee committee);
}
