package net.blueshell.api.domain.event.persistence.repository

import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface GuestRepository : BaseRepository<Guest, Long> {
    fun findByAccessToken(accessToken: String): Optional<Guest>
}
