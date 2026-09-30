package net.blueshell.api.committee.web

import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.testsupport.countStatements
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The committees pages and the menu read `/committees`, so its statement count must not grow
 * with the committees: pictures, games and seats are batched rather than read one at a time.
 */
@SpringBootTest
class CommitteeListFetchIT : UserTestSupport() {
    @Test
    fun `reading the committees does not scale queries with the number of committees`() {
        val board = createUserWithRole(Role.BOARD)
        val pictured = {
            val committee = createCommitteeFixture(name = "Fetch ${System.nanoTime()}")
            committee.banner = createFileFixture(board, type = FileType.COMMITTEE_BANNER)
            committee.icon = createFileFixture(board, type = FileType.COMMITTEE_ICON)
            committee.gameCodes += "CS2"
            addCommitteeMember(committee, createUserWithRole(Role.MEMBER))
        }
        pictured()
        // Primed, so one-time statements for the session do not skew the first count.
        readCommittees()

        val before = countStatements { readCommittees() }
        repeat(4) { pictured() }
        val after = countStatements { readCommittees() }

        assertThat(after)
            .describedAs("query count must not grow with the number of committees (N+1)")
            .isEqualTo(before)
    }

    private fun readCommittees() {
        mvc.perform(get("/committees")).andExpect(status().isOk)
    }
}
