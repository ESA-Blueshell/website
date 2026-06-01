package net.blueshell.api.platform.integration.audience.persistence.repository

import net.blueshell.api.platform.integration.audience.persistence.Audience
import net.blueshell.api.platform.integration.audience.persistence.AudienceGroupKind
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

/**
 * `system` is a plain string holding a `TargetSystem.name()`; the
 * persistence layer cannot depend on the `sync.port` package.
 * Application code resolves the enum.
 */
@Repository
interface AudienceRepository : BaseRepository<Audience, Long> {
    fun findAllBySystem(system: String): List<Audience>

    fun findAllBySystemAndKind(system: String, kind: AudienceGroupKind): List<Audience>
}
