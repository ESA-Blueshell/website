package net.blueshell.api.committee.persistence

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.committee.web.dto.AdvancedCommitteeDTO
import net.blueshell.api.committee.web.dto.AdvancedCommitteeKonverter
import net.blueshell.api.committee.web.dto.CommitteeMemberDTO
import net.blueshell.api.committee.web.dto.CommitteeMemberKonverter
import net.blueshell.api.committee.web.dto.SimpleCommitteeDTO
import net.blueshell.api.committee.web.dto.SimpleCommitteeKonverter

private val committeeMemberKonverter = Konverter.get<CommitteeMemberKonverter>()
private val advancedCommitteeKonverter = Konverter.get<AdvancedCommitteeKonverter>()
private val simpleCommitteeKonverter = Konverter.get<SimpleCommitteeKonverter>()

fun CommitteeMember.asDto(): CommitteeMemberDTO = committeeMemberKonverter.toDTO(this)

fun Committee.asAdvancedDto(): AdvancedCommitteeDTO {
    val dto = advancedCommitteeKonverter.toDTO(this)
    dto.members = members.map { it.asDto() }.toMutableList()
    return dto
}

fun Committee.asSimpleDto(): SimpleCommitteeDTO = simpleCommitteeKonverter.toDTO(this)
