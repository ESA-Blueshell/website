package net.blueshell.api.repository

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.event.Guest
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface GuestRepository : BaseRepository<Guest> {
    fun findByAccessToken(accessToken: String): Optional<Guest>
}
