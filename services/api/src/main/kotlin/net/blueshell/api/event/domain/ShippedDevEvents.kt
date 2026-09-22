package net.blueshell.api.event.domain

import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.file.api.FileService
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.seed.SeedOrder
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * Puts the association's own committees and events into a development database on start.
 *
 * A dev database with no events draws every page that shows them as empty, which is the one
 * state those pages are built to hide, so a developer who has just created a database sees
 * neither the strip nor the calendar nor what either does when a title runs long.
 *
 * Development only, and only where there are no events yet: a database that already holds one
 * is left exactly as it stands, so a second start neither doubles the seed nor overwrites what
 * somebody has been editing. Production keeps its own events and never reaches this.
 */
@Component
@Profile("dev")
class ShippedDevEvents(
    private val committees: CommitteeService,
    private val events: EventRepository,
    private val files: FileService,
    private val users: UserService,
    private val transactions: TransactionTemplate,
) {
    /** What a run wrote, which is nothing at all on every start after the first. */
    data class Applied(
        val committees: Int,
        val events: Int,
        val banners: Int,
    )

    @Order(SeedOrder.ART)
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        val applied =
            try {
                apply()
            } catch (e: Exception) {
                log.warn("[dev-events] the events that ship could not be loaded: {}", e.message)
                return
            }
        if (applied.events > 0) {
            log.info(
                "[dev-events] {} committees, {} events and {} banners are now in the database",
                applied.committees,
                applied.events,
                applied.banners,
            )
        }
    }

    fun apply(): Applied {
        if (events.count() > 0) return Applied(0, 0, 0)
        val owner = siteAccount() ?: return Applied(0, 0, 0)

        val seeded = committeesFromSeed()
        var written = 0
        var drawn = 0
        EventSeed.files.rows(EventSeed.EVENTS).forEach { row ->
            val art = row[ART]?.ifBlank { null }?.let { name -> store(name, owner) }
            transactions.execute {
                val event = event(row, seeded)
                if (art != null) event.replaceBanner(EventBanner(event = event, file = art))
                events.save(event)
            }
            written += 1
            if (art != null) drawn += 1
        }
        return Applied(committees = seeded.size, events = written, banners = drawn)
    }

    /**
     * The committees the events are hung on.
     *
     * One already in the database is taken as it stands: the name is unique, so writing it
     * again is refused rather than merged.
     */
    private fun committeesFromSeed(): Map<String, Committee> {
        val held = committees.findAll().associateBy { it.name }
        return EventSeed.files.rows(EventSeed.COMMITTEES).associate { row ->
            val name = row.getValue(NAME)
            name to (
                held[name] ?: transactions.execute {
                    committees.create(Committee(name = name, description = row[DESCRIPTION].orEmpty()))
                }!!
            )
        }
    }

    /**
     * One event, as the file has it. A committee the file names and the database has lost
     * leaves the event without one, which is the state a deleted committee already puts its
     * events in.
     */
    private fun event(
        row: Map<String, String>,
        seeded: Map<String, Committee>,
    ) = Event(
        committee = seeded[row[COMMITTEE]],
        title = row.getValue(TITLE),
        description = row[DESCRIPTION]?.ifBlank { null },
        location = row[LOCATION]?.ifBlank { null },
        startTime = Instant.parse(row.getValue(START)),
        endTime = Instant.parse(row.getValue(END)),
        approved = true,
        membersOnly = row[MEMBERS_ONLY].toBoolean(),
        signUp = row[SIGN_UP].toBoolean(),
    )

    /** The banner an event ships with, stored the way an upload is. */
    private fun store(
        name: String,
        owner: User,
    ): File? {
        val resource = "${EventSeed.files.directory}/art/$name"
        return try {
            val bytes =
                javaClass.classLoader.getResourceAsStream(resource)
                    ?: error("Shipped event art $resource is missing")
            files.store(bytes, name, WEBP, FileType.EVENT_BANNER, owner)
        } catch (e: Exception) {
            log.warn("[dev-events] {} could not be stored: {}", name, e.message)
            null
        }
    }

    /**
     * The account the shipped art is credited to, which the migration that writes it puts
     * there. Answering with null rather than throwing keeps a database without one from being
     * the reason a start fails.
     */
    private fun siteAccount(): User? {
        val account = runCatching { users.findByUsername(SITE_ACCOUNT) }.getOrNull()
        if (account == null) log.warn("[dev-events] there is no '{}' account to credit the art to", SITE_ACCOUNT)
        return account
    }

    private companion object {
        val log = LoggerFactory.getLogger(ShippedDevEvents::class.java)
        const val NAME = "name"
        const val DESCRIPTION = "description"
        const val COMMITTEE = "committee"
        const val TITLE = "title"
        const val LOCATION = "location"
        const val START = "start_time"
        const val END = "end_time"
        const val MEMBERS_ONLY = "members_only"
        const val SIGN_UP = "sign_up"
        const val ART = "art"
        const val WEBP = "image/webp"
        const val SITE_ACCOUNT = "system"
    }
}
