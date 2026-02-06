package net.blueshell.api.repository

import net.blueshell.api.model.File
import net.blueshell.api.model.Sponsor
import net.blueshell.api.repository.base.BaseRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Suppress("FunctionName")
interface SponsorRepository : BaseRepository<Sponsor, Long> {
    fun findBy_picture(picture: File): Optional<Sponsor>
}
