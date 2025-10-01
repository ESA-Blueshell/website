package net.blueshell.api.mapper;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.CommitteeMemberDTO;
import net.blueshell.api.mapper.user.SimpleUserMapper;
import net.blueshell.api.model.committee.CommitteeMember;
import net.blueshell.api.service.CommitteeMemberService;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Mapper(componentModel = "spring", uses = {SimpleUserMapper.class}, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class CommitteeMemberMapper extends BaseMapper<CommitteeMember, CommitteeMemberDTO> {

    @Autowired
    private CommitteeMemberService committeeMemberService;

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "role")
    @Mapping(target = "userId")
    public abstract CommitteeMemberDTO toDTO(CommitteeMember member);

    @ObjectFactory
    public CommitteeMember create(CommitteeMemberDTO dto) {
        if (dto.getId() == null) {
            return new CommitteeMember();
        } else {
            return committeeMemberService.findById(dto.getId());
        }
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "role")
    @Mapping(target = "userId")
    public abstract CommitteeMember fromDTO(CommitteeMemberDTO dto, @MappingTarget CommitteeMember member);
}