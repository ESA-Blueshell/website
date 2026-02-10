package net.blueshell.api.committee.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface CommitteeMemberRepository : BaseRepository<CommitteeMember, CommitteeMember.Id> {
    fun countByUserId(userId: Long): Long
}
