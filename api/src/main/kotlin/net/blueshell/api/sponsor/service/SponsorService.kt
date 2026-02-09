package net.blueshell.api.sponsor.service

import jakarta.ws.rs.NotFoundException
import net.blueshell.api.file.model.File
import net.blueshell.api.sponsor.model.Sponsor
import net.blueshell.api.sponsor.repository.SponsorRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.function.Supplier

@Service
class SponsorService @Autowired constructor(repository: SponsorRepository, events: ApplicationEventPublisher) :
    BaseModelService<Sponsor, Long, SponsorRepository>(repository) {
    fun findByPicture(picture: File): Sponsor {
        return repository.findBy_picture(picture)
            .orElseThrow(Supplier { NotFoundException("Sponsor not found for picture: " + picture.name) })
    }
}
