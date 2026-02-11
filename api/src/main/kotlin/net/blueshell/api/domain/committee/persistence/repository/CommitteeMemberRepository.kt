package net.blueshell.api.domain.committee.persistence.repository

import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface CommitteeMemberRepository : BaseRepository<CommitteeMember, CommitteeMember.Id> {
    fun countByUserId(userId: Long): Long
}
