package net.blueshell.api.service

import jakarta.ws.rs.NotFoundException
import net.blueshell.api.base.BaseModelService
import net.blueshell.api.model.File
import net.blueshell.api.model.Sponsor
import net.blueshell.api.repository.SponsorRepository
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
