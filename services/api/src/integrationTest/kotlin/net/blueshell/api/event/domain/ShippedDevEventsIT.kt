package net.blueshell.api.event.domain

import net.blueshell.api.committee.persistence.CommitteeRepository
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.file.api.FileService
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * The committees, events and banners the repository ships land in a database that has none.
 *
 * The loader is built here rather than injected: it carries `@Profile("dev")` and the suite
 * runs under `test`, which is exactly the guard being relied on in production.
 */
class ShippedDevEventsIT : UserTestSupport() {
    @Autowired private lateinit var committees: CommitteeRepository

    @Autowired private lateinit var events: EventRepository

    @Autowired private lateinit var files: FileService

    @Autowired private lateinit var users: UserService

    private fun loader() = ShippedDevEvents(committees, events, files, users, transactionTemplate)

    @Test
    fun `the events the seed names are written with the committees they were run by`() {
        val applied = loader().apply()

        assertThat(applied.events).isEqualTo(EventSeed.files.rows(EventSeed.EVENTS).size)
        assertThat(applied.committees).isEqualTo(EventSeed.files.rows(EventSeed.COMMITTEES).size)
        assertThat(events.count()).isEqualTo(applied.events.toLong())
        assertThat(transactionTemplate.execute { events.findAll().count { it.committee != null } })
            .isEqualTo(applied.events)
    }

    @Test
    fun `an event the seed ships art for carries it as its banner`() {
        val withArt = EventSeed.files.rows(EventSeed.EVENTS).count { it["art"]?.isNotBlank() == true }

        val applied = loader().apply()

        assertThat(applied.banners).isEqualTo(withArt)
        assertThat(transactionTemplate.execute { events.findAll().count { it.banner != null } })
            .isEqualTo(withArt)
    }

    @Test
    fun `a second start leaves the database exactly as it stands`() {
        loader().apply()

        val again = loader().apply()

        assertThat(again).isEqualTo(ShippedDevEvents.Applied(0, 0, 0))
        assertThat(events.count()).isEqualTo(EventSeed.files.rows(EventSeed.EVENTS).size.toLong())
    }

    @Test
    fun `a committee the database already holds is taken as it stands`() {
        val name = EventSeed.files.rows(EventSeed.COMMITTEES).first().getValue("name")
        val held = createCommitteeFixture(name = name)

        loader().apply()

        assertThat(transactionTemplate.execute { committees.findAll().filter { it.name == name } })
            .singleElement()
            .extracting { it.id }
            .isEqualTo(held.id)
    }
}
