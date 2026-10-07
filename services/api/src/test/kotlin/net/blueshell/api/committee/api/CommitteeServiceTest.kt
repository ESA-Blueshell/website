package net.blueshell.api.committee.api

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeRepository
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.game.api.GameArchived
import net.blueshell.api.game.api.GameService
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorProvider
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.OptimisticLockingFailureException
import java.util.Optional

class CommitteeServiceTest {
    private val repository =
        mock<CommitteeRepository> {
            // A new committee gets its id on the save, as the database gives it one.
            on { saveAndFlush(any<Committee>()) } doAnswer { it.getArgument<Committee>(0).apply { id = id ?: 9 } }
        }
    private val pictures = mock<StoredPictures>()
    private val games = mock<GameService>()
    private val actors = mock<ActorProvider> { on { currentOrSystem() } doReturn Actor.system() }

    /** Events by the committee organising them, handed over the way the event module does it. */
    private val organised = mutableMapOf<Long, Long>()
    private val events =
        object : CommitteeEvents {
            override fun countOf(committeeId: Long) = organised[committeeId] ?: 0

            override fun handOver(
                from: Long,
                to: Long,
            ) {
                organised[to] = (organised[to] ?: 0) + (organised.remove(from) ?: 0)
            }
        }
    private val service =
        CommitteeService(
            repository,
            mock<UserService>(),
            TrackedEventPublisher(mock<AfterCommitEventPublisher>(), actors, mock()),
            pictures,
            games,
            listOf(events),
        )

    private fun committee(
        id: Long,
        name: String,
        vararg codes: String,
    ) = Committee(name = name, description = "About $name").apply {
        this.id = id
        gameCodes += codes
    }

    private fun stored(committee: Committee) = whenever(repository.findById(committee.id!!)).thenReturn(Optional.of(committee))

    @Test
    fun `finds a committee by its address whatever its case, and refuses one nobody answers to`() {
        val lan = committee(1, "LanCie")
        whenever(repository.findBySlug("lancie")).thenReturn(lan)

        assertThat(service.findByAddress(" LanCie ")).isSameAs(lan)
        assertThatThrownBy { service.findByAddress("gone") }.isInstanceOf(UnknownCommitteeAddress::class.java)
    }

    @Test
    fun `adds a committee at an address made from its name, with its banner and games`() {
        val banner = Entities.file()
        whenever(pictures.of("b.webp", FileType.COMMITTEE_BANNER)).thenReturn(banner)
        whenever(games.requireNameable(listOf("CHESS"), emptySet())).thenReturn(listOf("CHESS"))

        val page = CommitteePage(banner = "b.webp", gameCodes = listOf("CHESS"))
        val made = service.createWithMembers("Member's initiative", "Anything", emptyList(), page)

        assertThat(made.slug).isEqualTo("member-s-initiative")
        assertThat(made.banner).isSameAs(banner)
        assertThat(made.gameCodes).containsExactly("CHESS")
    }

    @Test
    fun `refuses an address that makes nothing, or one another committee answers to`() {
        whenever(repository.findBySlug("board")).thenReturn(committee(2, "Board"))

        assertThatThrownBy { service.createWithMembers("LanCie", "LANs", emptyList(), CommitteePage(address = " ?! ")) }
            .isInstanceOf(CommitteeAddressBlank::class.java)
        assertThatThrownBy { service.createWithMembers("LanCie", "LANs", emptyList(), CommitteePage(address = "Board")) }
            .isInstanceOf(CommitteeAddressTaken::class.java)
            .extracting("facts")
            .isEqualTo(mapOf("committeeName" to "Board", "address" to "board"))
    }

    @Test
    fun `corrects a committee's page, keeping its own address and its games where the save says nothing of them`() {
        val lan = committee(1, "LanCie", "CS2")
        stored(lan)
        whenever(repository.findBySlug("lan")).thenReturn(lan)

        val saved = service.updateWithMembers(1, "LanCie", "LANs, bigger", emptyList(), 0, CommitteePage(address = "lan"))

        assertThat(saved.slug).isEqualTo("lan")
        assertThat(saved.description).isEqualTo("LANs, bigger")
        assertThat(saved.gameCodes).containsExactly("CS2")
        assertThat(saved.version).isEqualTo(0)
    }

    @Test
    fun `refuses an edit or an own-page edit made against an older version`() {
        val lan = committee(1, "LanCie", "CS2").apply { version = 2 }
        stored(lan)

        assertThatThrownBy { service.updateWithMembers(1, "LanCie", "LANs", emptyList(), 1) }
            .isInstanceOf(OptimisticLockingFailureException::class.java)
        assertThatThrownBy { service.updateOwnPage(1, "LANs", null, null, null, 1) }
            .isInstanceOf(OptimisticLockingFailureException::class.java)
        assertThat(lan.description).isNotEqualTo("LANs")
    }

    @Test
    fun `lets a committee's own members change its description, pictures and games, keeping an archived game it names`() {
        val lan = committee(1, "LanCie", "CSGO")
        stored(lan)
        whenever(games.requireNameable(listOf("CSGO", "CS2"), setOf("CSGO"))).thenReturn(listOf("CSGO", "CS2"))

        val saved = service.updateOwnPage(1, "LANs, every month", null, null, listOf("CSGO", "CS2"), 0)
        assertThat(saved.description).isEqualTo("LANs, every month")
        assertThat(saved.banner).isNull()
        assertThat(saved.icon).isNull()
        assertThat(saved.gameCodes).containsExactly("CSGO", "CS2")

        service.updateOwnPage(1, "LANs", null, null, null, null)
        assertThat(lan.gameCodes).containsExactly("CSGO", "CS2")
    }

    @Test
    fun `adds and corrects a committee at an address from its name where nothing else is said`() {
        val lan = committee(1, "LanCie")
        stored(lan)

        assertThat(service.createWithMembers("Pub Quiz", "Questions", emptyList()).slug).isEqualTo("pub-quiz")
        assertThat(service.updateWithMembers(1, "LAN Cie", "LANs", emptyList(), null).slug).isEqualTo("lan-cie")
    }

    @Test
    fun `archives a committee and brings it back`() {
        val lan = committee(1, "LanCie")
        stored(lan)

        assertThat(service.archive(1, true).archived).isTrue()
        assertThat(service.archive(1, false).archived).isFalse()
    }

    @Test
    fun `sets the committees that organise events for a game from the game's side, writing only those that change`() {
        val lan = committee(1, "LanCie", "CS2")
        val circuit = committee(2, "CircuitCie")
        val fun4 = committee(3, "4FunCie")
        whenever(repository.findAll()).thenReturn(listOf(lan, circuit, fun4))
        whenever(games.requireCode(" cs2 ")).thenReturn("CS2")
        whenever(games.requireNameable(listOf("CS2"))).thenReturn(listOf("CS2"))

        val organisers = service.organisersOf(" cs2 ", setOf(2L))

        assertThat(organisers).containsExactly(circuit)
        assertThat(lan.gameCodes).isEmpty()
        verify(repository, never()).saveAndFlush(fun4)
    }

    @Test
    fun `an archived game keeps its committees but gains none`() {
        val lan = committee(1, "LanCie", "CSGO")
        val circuit = committee(2, "CircuitCie")
        whenever(repository.findAll()).thenReturn(listOf(lan, circuit))
        whenever(games.requireCode("CSGO")).thenReturn("CSGO")
        whenever(games.requireNameable(listOf("CSGO"))).thenThrow(GameArchived("CS:GO"))

        assertThat(service.organisersOf("CSGO", setOf(1L))).containsExactly(lan)
        assertThatThrownBy { service.organisersOf("CSGO", setOf(1L, 2L)) }.isInstanceOf(GameArchived::class.java)
    }

    @Test
    fun `reads every committee, and refuses one that does not exist with its own code`() {
        val lan = committee(1, "LanCie")
        whenever(repository.findAll()).thenReturn(mutableListOf(lan))
        whenever(repository.findById(404L)).thenReturn(Optional.empty())

        assertThat(service.findAll()).containsExactly(lan)
        assertThat(assertThrows<CommitteeNotFound> { service.findById(404L) }.code).isEqualTo("CommitteeNotFound")
    }

    @Test
    fun `saves a committee as given, and deletes one without events naming no taker`() {
        val lan = committee(1, "LanCie")
        stored(lan)

        assertThat(service.create(lan)).isSameAs(lan)
        assertThat(service.eventCount(1)).isZero()
        service.delete(1, null)

        verify(repository).delete(lan)
    }

    @Test
    fun `hands every event to the committee taking over before deleting, and refuses a deletion that would orphan them`() {
        val lan = committee(1, "LanCie")
        val events = committee(2, "Activitiescie")
        val archived = committee(3, "Oldcie").apply { this.archived = true }
        listOf(lan, events, archived).forEach(::stored)
        whenever(repository.findById(404L)).thenReturn(Optional.empty())
        organised[1] = 4

        assertThat(service.eventCount(1)).isEqualTo(4)
        assertThatThrownBy { service.delete(1, null) }
            .isInstanceOfSatisfying(CommitteeEventsNeedTaker::class.java) { assertThat(it.facts["events"]).isEqualTo(4L) }
        assertThatThrownBy { service.delete(1, 1) }.isInstanceOf(CommitteeCannotTakeOwnEvents::class.java)
        assertThatThrownBy { service.delete(1, 404) }.isInstanceOf(CommitteeNotFound::class.java)
        assertThatThrownBy { service.delete(1, 3) }
            .isInstanceOfSatisfying(ArchivedCommitteeCannotTakeEvents::class.java) {
                assertThat(it.facts["committeeName"]).isEqualTo("Oldcie")
            }
        assertThat(organised).containsExactlyEntriesOf(mapOf(1L to 4L))
        verify(repository, never()).delete(any<Committee>())

        service.delete(1, 2)

        assertThat(organised).containsExactlyEntriesOf(mapOf(2L to 4L))
        verify(repository).delete(lan)
    }
}
