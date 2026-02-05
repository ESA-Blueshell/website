package net.blueshell.api.service

import net.blueshell.api.service.base.BaseModelService
import net.blueshell.api.model.committee.CommitteeMember
import net.blueshell.api.model.committee.CommitteeMemberId
import net.blueshell.api.repository.committee.CommitteeMemberRepository
import org.springframework.stereotype.Service

@Service
class CommitteeMemberService(repository: CommitteeMemberRepository) :
    BaseModelService<CommitteeMember, CommitteeMemberId, CommitteeMemberRepository>(repository)
