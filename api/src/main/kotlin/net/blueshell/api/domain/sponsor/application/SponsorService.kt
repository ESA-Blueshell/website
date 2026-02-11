package net.blueshell.api.domain.sponsor.application

import jakarta.ws.rs.NotFoundException
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.service.BaseModelService
import net.blueshell.api.sponsor.persistence.Sponsor
import net.blueshell.api.sponsor.persistence.SponsorRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.function.Supplier

@Service
class SponsorService @Autowired constructor(repository: SponsorRepository, events: ApplicationEventPublisher) :
    BaseModelService<Sponsor, Long, SponsorRepository>(repository) {
    fun findByPicture(picture: File): Sponsor {
        return repository.findByPicture(picture)
            .orElseThrow(Supplier { NotFoundException("Sponsor not found for picture: " + picture.name) })
    }
}
