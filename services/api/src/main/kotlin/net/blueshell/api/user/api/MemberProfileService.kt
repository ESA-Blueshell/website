package net.blueshell.api.user.api

import net.blueshell.api.shared.service.BaseModelService
import net.blueshell.api.user.persistence.MemberProfile
import net.blueshell.api.user.persistence.MemberProfileRepository
import org.springframework.stereotype.Service

@Service
class MemberProfileService(
    repository: MemberProfileRepository,
) : BaseModelService<MemberProfile, Long, MemberProfileRepository>(repository)
