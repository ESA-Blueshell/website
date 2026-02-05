package net.blueshell.api.mapper.committee

import net.blueshell.api.mapper.base.BaseMapper
import net.blueshell.api.dto.committee.CommitteeMemberDTO
import net.blueshell.api.mapper.user.SimpleUserMapper
import net.blueshell.api.model.committee.CommitteeMember
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget

@Mapper(componentModel = "spring", uses = [SimpleUserMapper::class])
abstract class CommitteeMemberMapper : BaseMapper<CommitteeMember, CommitteeMemberDTO>() {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "role")
    @Mapping(target = "committeeId")
    @Mapping(target = "userId")
    @Mapping(target = "version")
    abstract fun fromDTO(dto: CommitteeMemberDTO, @MappingTarget member: CommitteeMember): CommitteeMember

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "role")
    @Mapping(target = "committeeId")
    @Mapping(target = "userId")
    @Mapping(target = "version")
    abstract override fun toDTO(member: CommitteeMember): CommitteeMemberDTO
}
