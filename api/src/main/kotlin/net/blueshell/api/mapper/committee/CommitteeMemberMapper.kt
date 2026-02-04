package net.blueshell.api.mapper.committee

import net.blueshell.api.base.BaseMapper
import net.blueshell.api.dto.committee.CommitteeMemberDTO
import net.blueshell.api.mapper.user.SimpleUserMapper
import net.blueshell.api.model.committee.CommitteeMember
import net.blueshell.api.service.CommitteeMemberService
import org.mapstruct.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@Mapper(componentModel = "spring", uses = [SimpleUserMapper::class])
abstract class CommitteeMemberMapper : BaseMapper<CommitteeMember, CommitteeMemberDTO>() {
    @Autowired
    private lateinit var committeeMemberService: CommitteeMemberService

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
