package net.blueshell.api.event.persistence

import net.blueshell.api.event.persistence.Guest
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface GuestRepository : BaseRepository<Guest, Long> {
    fun findByAccessToken(accessToken: String): Optional<Guest>
}
