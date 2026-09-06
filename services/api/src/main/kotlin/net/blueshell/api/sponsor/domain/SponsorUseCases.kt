package net.blueshell.api.sponsor.domain

import net.blueshell.api.sponsor.persistence.Sponsor
import org.springframework.stereotype.Service

/**
 * Write operations on sponsors that build or mutate an entity. Reads and deletes
 * go straight to [SponsorService] from the controller.
 */
@Service
class SponsorUseCases(
    private val service: SponsorService,
) {
    fun create(name: String, description: String): Sponsor =
        service.create(Sponsor(name = name, description = description))

    fun update(id: Long, name: String, description: String, version: Long): Sponsor {
        val sponsor = service.findById(id).apply {
            this.name = name
            this.description = description
            this.version = version
        }
        return service.update(sponsor)
    }
}
