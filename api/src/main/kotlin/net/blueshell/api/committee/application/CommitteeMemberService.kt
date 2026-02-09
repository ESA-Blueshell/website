package net.blueshell.api.committee.application

import net.blueshell.api.committee.domain.model.CommitteeMember
import net.blueshell.api.committee.persistence.CommitteeMemberRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.stereotype.Service

@Service
class CommitteeMemberService(repository: CommitteeMemberRepository) :
    BaseModelService<CommitteeMember, CommitteeMember.Id, CommitteeMemberRepository>(repository)
