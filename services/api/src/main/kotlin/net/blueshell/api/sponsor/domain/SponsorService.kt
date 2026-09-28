package net.blueshell.api.sponsor.domain

import net.blueshell.api.shared.service.BaseModelService
import net.blueshell.api.sponsor.persistence.Sponsor
import net.blueshell.api.sponsor.persistence.SponsorRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class SponsorService
    @Autowired
    constructor(
        repository: SponsorRepository,
        events: ApplicationEventPublisher,
    ) : BaseModelService<Sponsor, Long, SponsorRepository>(repository)
