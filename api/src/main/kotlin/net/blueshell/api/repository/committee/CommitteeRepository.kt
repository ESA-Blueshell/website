package net.blueshell.api.repository.committee

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.committee.Committee
import org.springframework.stereotype.Repository

@Repository
interface CommitteeRepository : BaseRepository<Committee, Long> {
    fun findAllBy_membersUserIdEquals(userId: Long): MutableList<Committee>
}
