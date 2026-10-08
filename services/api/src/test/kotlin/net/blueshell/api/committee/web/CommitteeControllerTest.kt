package net.blueshell.api.committee.web

import net.blueshell.api.committee.api.CommitteePage
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.domain.CommitteeSeat
import net.blueshell.api.committee.domain.CommitteeSeats
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.file.api.FileService
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant

class CommitteeControllerTest {
    private val service = mock<CommitteeService>()
    private val seats = mock<CommitteeSeats>()
    private val files = mock<FileService>()
    private val controller = CommitteeController(service, seats, files)

    private val lan =
        Committee(name = "LanCie", description = "LANs", slug = "lan", archived = true, archivedAt = Instant.EPOCH).apply {
            id = 1
            gameCodes += listOf("VALORANT", "CS2")
            createdAt = Instant.EPOCH
            updatedAt = Instant.EPOCH
        }

    @Test
    fun `says how many events a deletion hands over, and deletes with the committee taking over`() {
        whenever(service.eventCount(1)).thenReturn(3)

        assertThat(controller.findCommitteeDeletion(1).events).isEqualTo(3)
        controller.deleteCommitteeById(1, 2)

        verify(service).delete(1, 2)
    }

    @Test
    fun `answers a committee's page by its address, its members by Discord or username`() {
        whenever(service.findByAddress("lan")).thenReturn(lan)
        whenever(seats.of(lan)).thenReturn(listOf(CommitteeSeat("nelly", "https://cdn/n.png", true, "Chair")))

        val page = controller.findCommitteePage("lan")

        assertThat(page.archivedAt).isEqualTo(Instant.EPOCH)
        assertThat(page).isEqualTo(
            CommitteePageResponse(
                id = 1,
                name = "LanCie",
                slug = "lan",
                description = "LANs",
                archived = true,
                archivedAt = Instant.EPOCH,
                banner = null,
                icon = null,
                gameCodes = listOf("CS2", "VALORANT"),
                members = listOf(CommitteeSeatResponse("nelly", "https://cdn/n.png", true, "Chair")),
            ),
        )
    }

    @Test
    fun `passes the board's page fields through on a new committee and on a correction`() {
        whenever(service.createWithMembers(any(), any(), any(), any())).thenReturn(lan)
        whenever(service.updateWithMembers(any(), any(), any(), any(), anyOrNull(), any())).thenReturn(lan)
        val create = CreateCommitteeRequest("LanCie", "LANs", slug = "lan", banner = "b.webp", gameCodes = listOf("CS2"))
        val update = UpdateCommitteeRequest("LanCie", "LANs", version = 2, slug = "lan")

        val made = controller.createCommittee(create)
        controller.updateCommittee(1, update)

        assertThat(made.slug).isEqualTo("lan")
        assertThat(made.gameCodes).containsExactly("CS2", "VALORANT")
        verify(service)
            .createWithMembers(
                eq("LanCie"),
                eq("LANs"),
                eq(mutableListOf()),
                eq(CommitteePage("lan", "b.webp", gameCodes = listOf("CS2"))),
            )
        verify(service)
            .updateWithMembers(eq(1), eq("LanCie"), eq("LANs"), eq(mutableListOf()), eq(2), eq(CommitteePage("lan")))
    }

    @Test
    fun `lets the committee's own members save its page and store its banner and its logo`() {
        whenever(service.updateOwnPage(1, "LANs, monthly", "b.webp", "i.webp", listOf("CS2"), 3)).thenReturn(lan)
        val stored = Entities.file(path = "committee-banners/b.webp")
        val upload = MockMultipartFile("file", "b.png", "image/png", byteArrayOf(1))
        whenever(files.storeMultipart(upload, FileType.COMMITTEE_BANNER)).thenReturn(stored)
        whenever(files.storeMultipart(upload, FileType.COMMITTEE_ICON)).thenReturn(stored)

        controller.updateCommitteePage(1, CommitteeOwnPageRequest("LANs, monthly", "b.webp", "i.webp", listOf("CS2"), 3))
        controller.uploadCommitteeBanner(1, upload)
        controller.uploadCommitteeIcon(1, upload)

        verify(service).updateOwnPage(1, "LANs, monthly", "b.webp", "i.webp", listOf("CS2"), 3)
        verify(files).storeMultipart(upload, FileType.COMMITTEE_BANNER)
        verify(files).storeMultipart(upload, FileType.COMMITTEE_ICON)
    }

    @Test
    fun `archives a committee, and sets a game's organisers from the game's side`() {
        whenever(service.archive(1, true)).thenReturn(lan)
        whenever(service.organisersOf("CS2", setOf(1L))).thenReturn(listOf(lan))

        assertThat(controller.archiveCommittee(1, ArchiveCommitteeRequest(true)).archived).isTrue()
        assertThat(controller.setGameOrganisers("CS2", GameOrganisersRequest(listOf(1L))).map { it.slug })
            .containsExactly("lan")
    }

    /** Who sits on a committee is the board's and its own members' to know (#89). */
    @Nested
    inner class `who sits on a committee` {
        private val seated =
            Committee(name = "Sitecie", description = "The site", slug = "site").apply {
                id = 2
                createdAt = Instant.EPOCH
                updatedAt = Instant.EPOCH
                replaceMembers(
                    listOf(
                        CommitteeMember(committee = this, user = Entities.user(id = 7L), role = "Chair").apply {
                            createdAt = Instant.EPOCH
                            updatedAt = Instant.EPOCH
                        },
                    ),
                )
            }

        private fun reading(
            id: Long,
            vararg roles: Role,
        ) {
            val principal = UserPrincipal(id, "reader", "h", true, roles.toSet(), null, null)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        }

        @AfterEach
        fun forget() = SecurityContextHolder.clearContext()

        @BeforeEach
        fun committees() {
            whenever(service.findAll()).thenReturn(mutableListOf(seated))
            whenever(service.findById(2)).thenReturn(seated)
            whenever(service.findAllByUserId(7)).thenReturn(mutableListOf(seated))
        }

        @Test
        fun `a visitor reads the committees without their members, and has none of their own`() {
            assertThat(controller.findCommittees().map { it.members }).containsOnlyNulls()
            assertThat(controller.findCommitteesByUserId()).isEmpty()
        }

        @Test
        fun `a member reads the members of their own committee only`() {
            reading(7, Role.MEMBER)
            assertThat(controller.findCommittees().map { it.members }).containsOnlyNulls()
            assertThat(controller.findCommitteeById(2).members?.map { it.role }).containsExactly("Chair")
            assertThat(controller.findCommitteesByUserId().single().members).hasSize(1)

            reading(8, Role.MEMBER)
            assertThat(controller.findCommitteeById(2).members).isNull()
        }

        @Test
        fun `the board reads every committee with its members`() {
            reading(9, Role.BOARD)
            whenever(service.findAll()).thenReturn(mutableListOf(seated, lan))

            assertThat(controller.findCommittees().map { it.members?.size }).containsExactly(1, 0)
            assertThat(controller.findCommitteeById(2).members).hasSize(1)
            assertThat(controller.findCommitteesByUserId().map { it.slug }).containsExactly("site", "lan")
        }
    }
}
