package net.blueshell.api.committee.web.mapper

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.committee.web.dto.CommitteeMemberDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.committee.persistence.CommitteeMember
import org.springframework.stereotype.Component

@Konverter
interface CommitteeMemberKonverter {
    fun toDTO(member: CommitteeMember): CommitteeMemberDTO

    fun fromDTO(dto: CommitteeMemberDTO): CommitteeMember
}

@Component
class CommitteeMemberMapper : BaseMapper<CommitteeMember, CommitteeMemberDTO>() {
    private val konverter = konverter<CommitteeMemberKonverter>()

    override fun fromDTO(dto: CommitteeMemberDTO): CommitteeMember = konverter.fromDTO(dto)

    fun fromDTO(dto: CommitteeMemberDTO, member: CommitteeMember): CommitteeMember {
        val mapped = konverter.fromDTO(dto)
        member.committeeId = mapped.committeeId
        member.userId = mapped.userId
        member.role = mapped.role
        dto.version?.let { member.version = it }
        return member
    }

    override fun toDTO(entity: CommitteeMember): CommitteeMemberDTO = konverter.toDTO(entity)
}

fun CommitteeMember.asDTO(mapper: CommitteeMemberMapper): CommitteeMemberDTO = mapper.toDTO(this)

fun CommitteeMemberDTO.asEntity(mapper: CommitteeMemberMapper): CommitteeMember = mapper.fromDTO(this)
