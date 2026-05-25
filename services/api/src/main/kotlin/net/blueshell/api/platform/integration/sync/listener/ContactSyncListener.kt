package net.blueshell.api.platform.integration.sync.listener

import net.blueshell.api.domain.user.application.event.UserCreated
import net.blueshell.api.domain.user.application.event.UserDeleted
import net.blueshell.api.domain.user.application.event.UserUpdated
import net.blueshell.api.platform.integration.sync.application.ContactSyncService
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/** Modulith listener that fans user lifecycle events out to every contact target. */
@Component
class ContactSyncListener(
    private val service: ContactSyncService,
) {
    @ApplicationModuleListener
    fun on(event: UserCreated) = service.sync(event.userId)

    @ApplicationModuleListener
    fun on(event: UserUpdated) = service.sync(event.userId)

    @ApplicationModuleListener
    fun on(event: UserDeleted) = service.remove(event.userId)
}
