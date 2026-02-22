package net.blueshell.api.domain.user.application

import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.domain.user.persistence.repository.MemberProfileRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.stereotype.Service

@Service
class MemberProfileService(repository: MemberProfileRepository) :
    BaseModelService<MemberProfile, Long, MemberProfileRepository>(repository)