package net.blueshell.api.platform.integration.audience.persistence.repository

import net.blueshell.api.platform.integration.audience.persistence.Audience
import net.blueshell.api.platform.integration.audience.persistence.AudienceGroupKind
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface AudienceRepository : BaseRepository<Audience, Long> {
    fun findAllBySystem(system: TargetSystem): List<Audience>

    fun findAllBySystemAndKind(system: TargetSystem, kind: AudienceGroupKind): List<Audience>
}
