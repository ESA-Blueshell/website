package net.blueshell.api.sponsor.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface SponsorRepository : BaseRepository<Sponsor, Long>
