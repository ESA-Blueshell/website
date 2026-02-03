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
    @Mapping(target = "id")
    @Mapping(target = "role")
    @Mapping(target = "userId")
    @Mapping(target = "version")
    abstract override fun toDTO(member: CommitteeMember): CommitteeMemberDTO

    @ObjectFactory
    fun create(dto: CommitteeMemberDTO): CommitteeMember {
        CommitteeMemberMapper.log.info("creating a new committee member for dto {}", dto)
        if (dto.getId() == null) {
            return CommitteeMember()
        } else {
            return committeeMemberService.findById(dto.getId())
        }
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "role")
    @Mapping(target = "userId")
    @Mapping(target = "version")
    abstract fun fromDTO(dto: CommitteeMemberDTO, @MappingTarget member: CommitteeMember): CommitteeMember

    companion object {
        private val log = LoggerFactory.getLogger(CommitteeMemberMapper::class.java)
    }
}
