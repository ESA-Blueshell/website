package net.blueshell.api.repository

import net.blueshell.api.model.event.Guest
import net.blueshell.api.repository.base.BaseRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface GuestRepository : BaseRepository<Guest, Long> {
    fun findByAccessToken(accessToken: String): Optional<Guest>
}
