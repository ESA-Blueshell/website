package net.blueshell.api.domain.sponsor.application

import net.blueshell.api.domain.sponsor.command.result.SponsorResult
import net.blueshell.api.domain.sponsor.command.result.toResult
import net.blueshell.api.domain.sponsor.persistence.Sponsor
import org.springframework.stereotype.Service

/**
 * Write operations on sponsors that build or mutate an entity. Reads and deletes
 * go straight to [SponsorService] from the controller.
 */
@Service
class SponsorUseCases(
    private val service: SponsorService,
) {
    fun create(name: String, description: String): SponsorResult =
        service.create(Sponsor(name = name, description = description)).toResult()

    fun update(id: Long, name: String, description: String, version: Long): SponsorResult {
        val sponsor = service.findById(id).apply {
            this.name = name
            this.description = description
            this.version = version
        }
        return service.update(sponsor).toResult()
    }
}
