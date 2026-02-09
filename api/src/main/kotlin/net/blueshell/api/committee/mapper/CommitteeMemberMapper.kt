package net.blueshell.api.committee.mapper

import net.blueshell.api.committee.dto.CommitteeMemberDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.user.mapper.SimpleUserMapper
import net.blueshell.api.committee.model.CommitteeMember
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
