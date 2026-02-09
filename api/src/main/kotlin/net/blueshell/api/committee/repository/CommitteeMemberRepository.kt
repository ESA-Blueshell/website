package net.blueshell.api.committee.repository

import net.blueshell.api.committee.model.CommitteeMember
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface CommitteeMemberRepository : BaseRepository<CommitteeMember, CommitteeMember.Id>
