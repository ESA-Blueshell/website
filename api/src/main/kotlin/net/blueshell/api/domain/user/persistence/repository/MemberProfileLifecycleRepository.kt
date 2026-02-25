package net.blueshell.api.domain.user.persistence.repository

import net.blueshell.api.domain.user.persistence.lifecycle.MemberProfileLifecycle
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface MemberProfileLifecycleRepository : BaseRepository<MemberProfileLifecycle, Long>
