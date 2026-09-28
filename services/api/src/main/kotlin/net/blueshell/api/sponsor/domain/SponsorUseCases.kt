package net.blueshell.api.sponsor.domain

import net.blueshell.api.shared.refusal.Refusal
import net.blueshell.api.sponsor.persistence.Sponsor
import net.blueshell.api.sponsor.persistence.SponsorRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Every sponsor read and write, straight against the repository. */
@Service
class SponsorUseCases(
    private val sponsors: SponsorRepository,
) {
    @Transactional(readOnly = true)
    fun all(): List<Sponsor> = sponsors.findAll()

    @Transactional(readOnly = true)
    fun byId(id: Long): Sponsor = sponsors.findById(id).orElseThrow { SponsorNotFound(id) }

    @Transactional
    fun create(
        name: String,
        description: String,
    ): Sponsor = sponsors.saveAndFlush(Sponsor(name = name, description = description))

    @Transactional
    fun update(
        id: Long,
        name: String,
        description: String,
        version: Long,
    ): Sponsor {
        val sponsor =
            byId(id).apply {
                requireVersion(version)
                this.name = name
                this.description = description
            }
        return sponsors.saveAndFlush(sponsor)
    }

    @Transactional
    fun remove(id: Long) = sponsors.delete(byId(id))
}

class SponsorNotFound(
    id: Long,
) : Refusal(HttpStatus.NOT_FOUND, "SponsorNotFound", "That sponsor does not exist.", mapOf("id" to id))
