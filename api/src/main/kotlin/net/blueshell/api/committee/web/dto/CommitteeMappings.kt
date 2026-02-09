package net.blueshell.api.committee.web.dto

import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember

@Konverter
interface CommitteeMemberKonverter {
    fun toDTO(member: CommitteeMember): CommitteeMemberDTO

    fun fromDTO(dto: CommitteeMemberDTO): CommitteeMember
}

@Konverter
interface AdvancedCommitteeKonverter {
    @Konvert(mappings = [Mapping(target = "members", ignore = true)])
    fun toDTO(committee: Committee): AdvancedCommitteeDTO

    @Konvert(mappings = [Mapping(target = "members", ignore = true)])
    fun fromDTO(dto: AdvancedCommitteeDTO): Committee
}

@Konverter
interface SimpleCommitteeKonverter {
    fun toDTO(committee: Committee): SimpleCommitteeDTO

    fun fromDTO(dto: SimpleCommitteeDTO): Committee
}

private val committeeMemberKonverter = Konverter.get<CommitteeMemberKonverter>()
private val advancedCommitteeKonverter = Konverter.get<AdvancedCommitteeKonverter>()
private val simpleCommitteeKonverter = Konverter.get<SimpleCommitteeKonverter>()

fun CommitteeMemberDTO.asEntity(member: CommitteeMember = CommitteeMember()): CommitteeMember {
    val mapped = committeeMemberKonverter.fromDTO(this)
    return member.apply {
        committeeId = mapped.committeeId
        userId = mapped.userId
        role = mapped.role
        version?.let { this.version = it }
    }
}

fun AdvancedCommitteeDTO.asEntity(committee: Committee = Committee()): Committee {
    val mapped = advancedCommitteeKonverter.fromDTO(this)
    committee.name = mapped.name
    committee.description = mapped.description
    committee.members = members.map { it.asEntity() }
    committee.version = version
    return committee
}

fun SimpleCommitteeDTO.asEntity(): Committee = simpleCommitteeKonverter.fromDTO(this)
