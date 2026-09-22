package net.blueshell.api.event.domain

import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.EventSignUpRepository
import net.blueshell.api.survey.api.SurveyFactory
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
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate

/** The decisions the roster loader makes on its own, with the database mocked out. */
class ShippedDevSignUpsTest {
    private val events = mock<EventRepository>()
    private val signUps = mock<EventSignUpRepository>()
    private val committees = mock<CommitteeService>()
    private val users = mock<UserService>()

    private val manager = mock<PlatformTransactionManager>()
    private val transactions = TransactionTemplate(manager)

    init {
        whenever(manager.getTransaction(any())).thenReturn(mock<TransactionStatus>())
    }

    private val loader = ShippedDevSignUps(events, signUps, committees, SurveyFactory(), users, transactions)

    private val rosters = ShippedDevSignUps.ROSTERS
    private val seeded = rosters.sumOf { it.signUps.size }

    private fun database() {
        whenever(events.existsByTitle(any())).thenReturn(false)
        whenever(committees.findAll()).thenReturn(mutableListOf())
        whenever(events.save(any<Event>())).thenAnswer { it.arguments[0] }
        whenever(signUps.save(any<EventSignUp>())).thenAnswer { it.arguments[0] }
        whenever(users.findByUsername(any())).thenAnswer { account(nextId++) }
    }

    private var nextId = 1L

    private fun account(id: Long): User = mock<User>().also { whenever(it.id).thenReturn(id) }

    private fun written(): List<EventSignUp> {
        val captor = argumentCaptor<EventSignUp>()
        verify(signUps, times(seeded)).save(captor.capture())
        return captor.allValues
    }

    @Test
    fun `an empty database is given every event and sign-up the roster names`() {
        database()

        val applied = loader.apply()

        assertThat(applied).isEqualTo(ShippedDevSignUps.Applied(rosters.size, seeded))
        verify(events, times(rosters.size)).save(any())
    }

    @Test
    fun `a sign-up belongs to an account or to a guest, and carries the answers it was given`() {
        database()

        loader.apply()

        val rows = written()
        assertThat(rows).allSatisfy { assertThat(it.userId != null).isNotEqualTo(it.guest != null) }
        assertThat(rows.count { it.guest != null }).isEqualTo(rosters.sumOf { r -> r.signUps.count { it.guest != null } })
        assertThat(rows.map { it.answers.size }).contains(0, 3)
    }

    @Test
    fun `an event without a form takes no answers`() {
        database()
        val formless = rosters.first { it.form == null }

        loader.apply()

        val captor = argumentCaptor<Event>()
        verify(events, times(rosters.size)).save(captor.capture())
        val event = captor.allValues.first { it.title == formless.title }
        assertThat(event.signUpForm).isNull()
        assertThat(event.membersOnly).isEqualTo(formless.membersOnly)
    }

    @Test
    fun `a database that already holds the first event is left as it stands`() {
        whenever(events.existsByTitle(rosters.first().title)).thenReturn(true)

        assertThat(loader.apply()).isEqualTo(ShippedDevSignUps.Applied(0, 0))
        verify(events, never()).save(any())
    }

    @Test
    fun `an account the fixtures never seeded leaves its row out`() {
        database()
        val absent = rosters.first().signUps.first { it.username != null }.username!!
        whenever(users.findByUsername(absent)).thenThrow(RuntimeException("no such user"))

        val applied = loader.apply()

        assertThat(applied.signUps).isEqualTo(seeded - 1)
    }

    @Test
    fun `a start that cannot write the roster is not a start that fails`() {
        whenever(events.existsByTitle(any())).thenThrow(RuntimeException("the database is not there yet"))

        loader.onReady()

        verify(events, never()).save(any())
    }

    @Test
    fun `a start that writes the roster says what it wrote`() {
        database()

        loader.onReady()

        verify(events, times(rosters.size)).save(any())
    }
}
