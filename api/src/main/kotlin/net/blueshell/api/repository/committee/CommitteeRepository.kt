package net.blueshell.api.repository.committee

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.committee.Committee
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CommitteeRepository : BaseRepository<Committee, Long> {
    @Query("""select distinct c from Committee c join c._members m join m._user u where u.id = :userId """)
    fun findAllByUserId(@Param("userId") userId: Long): List<Committee>
}
