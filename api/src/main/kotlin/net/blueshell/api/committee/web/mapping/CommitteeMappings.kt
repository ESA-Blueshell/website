package net.blueshell.api.committee.web.mapping

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.committee.web.dto.AdvancedCommitteeDTO
import net.blueshell.api.committee.web.dto.CommitteeMemberDTO
import net.blueshell.api.committee.web.dto.SimpleCommitteeDTO

@Konverter
interface CommitteeMemberKonverter {
    fun toDTO(member: CommitteeMember): CommitteeMemberDTO

    fun fromDTO(dto: CommitteeMemberDTO): CommitteeMember
}

@Konverter
interface AdvancedCommitteeKonverter {
    fun toDTO(committee: Committee): AdvancedCommitteeDTO

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

fun CommitteeMemberDTO.asEntity(): CommitteeMember = committeeMemberKonverter.fromDTO(this)

fun AdvancedCommitteeDTO.asEntity(existing: Committee? = null): Committee {
    val mapped = advancedCommitteeKonverter.fromDTO(this)
    existing?.id?.let { mapped.assignIdForRef(it) }
    return mapped
}

fun SimpleCommitteeDTO.asEntity(): Committee = simpleCommitteeKonverter.fromDTO(this)

fun CommitteeMember.asDto(): CommitteeMemberDTO = committeeMemberKonverter.toDTO(this)

fun Committee.asAdvancedDto(): AdvancedCommitteeDTO = advancedCommitteeKonverter.toDTO(this)

fun Committee.asSimpleDto(): SimpleCommitteeDTO = simpleCommitteeKonverter.toDTO(this)
