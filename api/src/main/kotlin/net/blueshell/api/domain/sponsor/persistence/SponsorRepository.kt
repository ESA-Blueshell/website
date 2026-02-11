package net.blueshell.api.domain.sponsor.persistence

import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Suppress("FunctionName")
interface SponsorRepository : BaseRepository<Sponsor, Long> {
    fun findByPicture(picture: File): Optional<Sponsor>
}
