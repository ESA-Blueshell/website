package net.blueshell.api.domain.user.persistence.repository

import net.blueshell.api.domain.user.persistence.PersonDetails
import net.blueshell.api.shared.repository.BaseRepository
import java.util.Optional

interface PersonDetailsRepository : BaseRepository<PersonDetails, Long> {
    fun findByUser_Id(userId: Long): Optional<PersonDetails>
}
