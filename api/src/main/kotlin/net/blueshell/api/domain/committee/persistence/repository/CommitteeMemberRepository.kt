package net.blueshell.api.domain.committee.persistence.repository

import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
@Suppress("FunctionName")
interface CommitteeMemberRepository : BaseRepository<CommitteeMember, CommitteeMember.Id> {
    fun countByUser_Id(userId: Long): Long
}
