package net.blueshell.api.feature.committee.service

import net.blueshell.api.feature.committee.model.CommitteeMember
import net.blueshell.api.feature.committee.repository.CommitteeMemberRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.stereotype.Service

@Service
class CommitteeMemberService(repository: CommitteeMemberRepository) :
    BaseModelService<CommitteeMember, CommitteeMember.Id, CommitteeMemberRepository>(repository)
