package net.blueshell.api.repository.committee

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.committee.CommitteeMember
import net.blueshell.api.model.committee.CommitteeMemberId
import org.springframework.stereotype.Repository

@Repository
interface CommitteeMemberRepository : BaseRepository<CommitteeMember, CommitteeMemberId>
