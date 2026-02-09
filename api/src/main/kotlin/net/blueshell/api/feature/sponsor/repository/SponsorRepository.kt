package net.blueshell.api.feature.sponsor.repository

import net.blueshell.api.feature.file.model.File
import net.blueshell.api.feature.sponsor.model.Sponsor
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Suppress("FunctionName")
interface SponsorRepository : BaseRepository<Sponsor, Long> {
    fun findBy_picture(picture: File): Optional<Sponsor>
}
