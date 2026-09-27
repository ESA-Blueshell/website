package net.blueshell.api.committee.web

import net.blueshell.api.committee.persistence.Committee
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/** The shapes the committee routes take and answer with, read field by field. */
class CommitteeShapesTest {
    @Test
    fun `a member's page save leaves the banner, games and version out unless it says them`() {
        val request = CommitteeOwnPageRequest(description = "LANs")

        assertThat(listOf(request.banner, request.gameCodes, request.version)).containsOnlyNulls()
    }

    @Test
    fun `a committee answers its address, Listed, Archived, banner and games`() {
        val lan =
            Committee(name = "LanCie", description = "LANs", listed = false, archived = true).apply {
                id = 1
                gameCodes += "CS2"
                createdAt = Instant.EPOCH
                updatedAt = Instant.EPOCH
            }

        val summary = lan.asSummaryResponse()
        val seat = CommitteeSeatResponse(discordTag = "nelly", avatar = "https://cdn/n.png", role = "Chair")

        assertThat(listOf(summary.slug, summary.listed, summary.archived, summary.banner, summary.gameCodes))
            .containsExactly("lancie", false, true, null, listOf("CS2"))
        assertThat(listOf(seat.discordTag, seat.avatar, seat.role)).containsExactly("nelly", "https://cdn/n.png", "Chair")
    }
}
