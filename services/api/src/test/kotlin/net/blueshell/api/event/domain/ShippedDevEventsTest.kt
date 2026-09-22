package net.blueshell.api.event.domain

import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.file.api.FileService
import net.blueshell.api.file.persistence.File
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate

/**
 * What the loader does with the files it ships, with the database mocked out.
 *
 * The committees and events themselves are covered where a real database is, in
 * `ShippedDevEventsIT`. This is about the decisions the loader makes on its own: what it does on
 * a database that already holds events, without the account the art is credited to, and when
 * storing a banner fails.
 */
class ShippedDevEventsTest {
    private val committees = mock<CommitteeService>()
    private val events = mock<EventRepository>()
    private val files = mock<FileService>()
    private val users = mock<UserService>()

    private val manager = mock<PlatformTransactionManager>()
    private val transactions = TransactionTemplate(manager)

    init {
        // The template is the real one, so every callback the loader wraps runs inline.
        whenever(manager.getTransaction(any())).thenReturn(mock<TransactionStatus>())
    }

    private val loader = ShippedDevEvents(committees, events, files, users, transactions)

    private val rows = EventSeed.files.rows(EventSeed.EVENTS)
    private val named = EventSeed.files.rows(EventSeed.COMMITTEES)
    private val drawn = rows.count { it["art"]?.isNotBlank() == true }

    private fun database() {
        whenever(events.count()).thenReturn(0)
        whenever(users.findByUsername("system")).thenReturn(mock<User>())
        whenever(committees.findAll()).thenReturn(mutableListOf())
        whenever(committees.create(any())).thenAnswer { it.arguments[0] }
        whenever(files.store(any(), any(), any(), any(), any())).thenReturn(mock<File>())
    }

    @Test
    fun `an empty database is given every committee and event the files name`() {
        database()

        val applied = loader.apply()

        assertThat(applied).isEqualTo(ShippedDevEvents.Applied(named.size, rows.size, drawn))
        verify(events, times(rows.size)).save(any())
    }

    @Test
    fun `a committee already in the database is taken as it stands`() {
        database()
        val held = named.first().getValue("name")
        whenever(committees.findAll()).thenReturn(mutableListOf(Committee(name = held, description = "")))

        loader.apply()

        val written = argumentCaptor<Committee>()
        verify(committees, times(named.size - 1)).create(written.capture())
        assertThat(written.allValues.map { it.name }).doesNotContain(held)
    }

    @Test
    fun `a database that already holds an event is left as it stands`() {
        whenever(events.count()).thenReturn(1)

        assertThat(loader.apply()).isEqualTo(ShippedDevEvents.Applied(0, 0, 0))
        verifyNoInteractions(committees)
        verify(events, never()).save(any())
    }

    @Test
    fun `without the account the art is credited to nothing is written`() {
        whenever(events.count()).thenReturn(0)
        whenever(users.findByUsername("system")).thenThrow(RuntimeException("no such user"))

        assertThat(loader.apply()).isEqualTo(ShippedDevEvents.Applied(0, 0, 0))
        verify(events, never()).save(any())
    }

    @Test
    fun `an event whose banner cannot be stored is written without one`() {
        database()
        whenever(files.store(any(), any(), any(), any(), any())).thenThrow(RuntimeException("the disk is full"))

        val applied = loader.apply()

        assertThat(applied.banners).isEqualTo(0)
        assertThat(applied.events).isEqualTo(rows.size)
        val written = argumentCaptor<Event>()
        verify(events, times(rows.size)).save(written.capture())
        assertThat(written.allValues).allSatisfy { assertThat(it.banner).isNull() }
    }

    @Test
    fun `a start that cannot load the seed is not a start that fails`() {
        whenever(events.count()).thenThrow(RuntimeException("the database is not there yet"))

        loader.onReady()

        verify(events, never()).save(any())
    }

    @Test
    fun `a start that loads the seed says what it wrote`() {
        database()

        loader.onReady()

        verify(events, times(rows.size)).save(any())
    }
}
