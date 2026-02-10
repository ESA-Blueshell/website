package net.blueshell.api.committee.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CommitteeRepository : BaseRepository<Committee, Long> {
    @Query("""select distinct c from Committee c join c._members m join m._user u where u.id = :userId """)
    fun findAllByUserId(@Param("userId") userId: Long): List<Committee>
}
