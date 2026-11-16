package net.blueshell.api.vote.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface VoteGameRepository : BaseRepository<VoteGame, Long> {
    fun findByIgdbId(igdbId: Long): VoteGame?
}
