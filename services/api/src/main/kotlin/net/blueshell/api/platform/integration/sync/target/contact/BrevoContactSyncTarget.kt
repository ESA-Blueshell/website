package net.blueshell.api.platform.integration.sync.target.contact

import net.blueshell.api.platform.integration.contact.adapter.brevo.BrevoContactAdapter
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!test & !dev")
class BrevoContactSyncTarget(adapter: BrevoContactAdapter) :
    ContactAdapterSyncTarget(adapter, TargetSystem.BREVO)
