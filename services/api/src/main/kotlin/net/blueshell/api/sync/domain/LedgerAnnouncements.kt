package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.AnnouncementLedger
import org.springframework.stereotype.Component

/** Whether an event's events-info post is out, as the post ledger records it. */
@Component
class LedgerAnnouncements(
    private val ledger: PostLedger,
) : AnnouncementLedger {
    override fun announced(eventId: Long): Boolean = ledger.find(eventId, DiscordArtefact.INFO_POST) != null
}
