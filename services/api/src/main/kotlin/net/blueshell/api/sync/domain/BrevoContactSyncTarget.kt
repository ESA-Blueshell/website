package net.blueshell.api.sync.domain

import net.blueshell.api.contact.api.BrevoContactAdapter
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!test & !dev")
class BrevoContactSyncTarget(adapter: BrevoContactAdapter) :
    ContactAdapterSyncTarget(adapter, TargetSystem.BREVO)
