package net.blueshell.api.domain.user.persistence.repository

import net.blueshell.api.domain.user.persistence.DeletedUser
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface DeletedUserRepository : JpaRepository<DeletedUser, Long> {
    fun findAllByOrderByRestoreUntilAtAsc(pageable: Pageable): Page<DeletedUser>

    fun findByRestoreUntilAtLessThanEqualOrderByRestoreUntilAtAsc(
        restoreUntilAt: Instant,
        pageable: Pageable
    ): List<DeletedUser>
}
