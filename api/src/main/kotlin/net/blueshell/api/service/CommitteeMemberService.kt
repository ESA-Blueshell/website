package net.blueshell.api.service

import net.blueshell.api.base.BaseModelService
import net.blueshell.api.model.committee.CommitteeMember
import net.blueshell.api.repository.committee.CommitteeMemberRepository
import org.springframework.stereotype.Service

@Service
class CommitteeMemberService(repository: CommitteeMemberRepository) :
    BaseModelService<CommitteeMember, CommitteeMember.CommitteeMemberId, CommitteeMemberRepository>(repository)
