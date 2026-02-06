package net.blueshell.api.repository.committee

import net.blueshell.api.model.committee.CommitteeMember
import net.blueshell.api.repository.base.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface CommitteeMemberRepository : BaseRepository<CommitteeMember, CommitteeMember.Id>
