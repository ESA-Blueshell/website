package net.blueshell.api.service

import net.blueshell.api.service.base.BaseModelService
import net.blueshell.api.model.event.Guest
import net.blueshell.api.repository.GuestRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.function.Supplier

@Service
class GuestService @Autowired constructor(repository: GuestRepository, events: ApplicationEventPublisher) :
    BaseModelService<Guest, Long, GuestRepository>(repository) {
    @Transactional(readOnly = true)
    fun findByAccessToken(accessToken: String): Guest {
        return repository!!.findByAccessToken(accessToken)
            .orElseThrow(Supplier {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Guest not found"
                )
            })
    }
}
