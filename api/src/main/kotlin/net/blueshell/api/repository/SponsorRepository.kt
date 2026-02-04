package net.blueshell.api.repository

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.File
import net.blueshell.api.model.Sponsor
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SponsorRepository : BaseRepository<Sponsor, Long> {
    fun findByPicture(picture: File): Optional<Sponsor>
}
