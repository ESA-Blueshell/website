package net.blueshell.api.committee.web

import net.blueshell.api.committee.api.CommitteePage
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.domain.CommitteeSeat
import net.blueshell.api.committee.domain.CommitteeSeats
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.file.api.FileService
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import java.time.Instant

class CommitteeControllerTest {
    private val service = mock<CommitteeService>()
    private val seats = mock<CommitteeSeats>()
    private val files = mock<FileService>()
    private val controller = CommitteeController(service, seats, files)

    private val lan =
        Committee(name = "LanCie", description = "LANs", slug = "lan", listed = false, archived = true).apply {
            id = 1
            gameCodes += listOf("VALORANT", "CS2")
            createdAt = Instant.EPOCH
            updatedAt = Instant.EPOCH
        }

    @Test
    fun `answers a committee's page by its address, its members by Discord only`() {
        whenever(service.findByAddress("lan")).thenReturn(lan)
        whenever(seats.of(lan)).thenReturn(listOf(CommitteeSeat("nelly", "https://cdn/n.png", "Chair")))

        val page = controller.findCommitteePage("lan")

        assertThat(page).isEqualTo(
            CommitteePageResponse(
                id = 1,
                name = "LanCie",
                slug = "lan",
                description = "LANs",
                listed = false,
                archived = true,
                banner = null,
                gameCodes = listOf("CS2", "VALORANT"),
                members = listOf(CommitteeSeatResponse("nelly", "https://cdn/n.png", "Chair")),
            ),
        )
    }

    @Test
    fun `passes the board's page fields through on a new committee and on a correction`() {
        whenever(service.createWithMembers(any(), any(), any(), any())).thenReturn(lan)
        whenever(service.updateWithMembers(any(), any(), any(), any(), anyOrNull(), any())).thenReturn(lan)
        val create = CreateCommitteeRequest("LanCie", "LANs", slug = "lan", listed = false, banner = "b.webp", gameCodes = listOf("CS2"))
        val update = UpdateCommitteeRequest("LanCie", "LANs", version = 2, slug = "lan", listed = false)

        val made = controller.createCommittee(create)
        controller.updateCommittee(1, update)

        assertThat(made.slug).isEqualTo("lan")
        assertThat(made.gameCodes).containsExactly("CS2", "VALORANT")
        verify(service)
            .createWithMembers(eq("LanCie"), eq("LANs"), eq(mutableListOf()), eq(CommitteePage("lan", false, "b.webp", listOf("CS2"))))
        verify(service)
            .updateWithMembers(eq(1), eq("LanCie"), eq("LANs"), eq(mutableListOf()), eq(2), eq(CommitteePage("lan", false, null, null)))
    }

    @Test
    fun `lets the committee's own members save its page and store its banner`() {
        whenever(service.updateOwnPage(1, "LANs, monthly", "b.webp", listOf("CS2"), 3)).thenReturn(lan)
        val stored = mock<File> { on { path } doReturn "committee-banners/b.webp" }
        val upload = MockMultipartFile("file", "b.png", "image/png", byteArrayOf(1))
        whenever(files.storeMultipart(upload, FileType.COMMITTEE_BANNER)).thenReturn(stored)

        controller.updateCommitteePage(1, CommitteeOwnPageRequest("LANs, monthly", "b.webp", listOf("CS2"), 3))
        controller.uploadCommitteeBanner(1, upload)

        verify(service).updateOwnPage(1, "LANs, monthly", "b.webp", listOf("CS2"), 3)
        verify(files).storeMultipart(upload, FileType.COMMITTEE_BANNER)
    }

    @Test
    fun `archives a committee, and sets a game's organisers from the game's side`() {
        whenever(service.archive(1, true)).thenReturn(lan)
        whenever(service.organisersOf("CS2", setOf(1L))).thenReturn(listOf(lan))

        assertThat(controller.archiveCommittee(1, ArchiveCommitteeRequest(true)).archived).isTrue()
        assertThat(controller.setGameOrganisers("CS2", GameOrganisersRequest(listOf(1L))).map { (it as CommitteeSummaryResponse).slug })
            .containsExactly("lan")
    }
}
