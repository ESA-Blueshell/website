package net.blueshell.api.board.api

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

/** Kandi is the board not yet in office; on its first day its members serve and Kandi lets them go. */
@SpringBootTest
class BoardHandoverIT : UserTestSupport() {
    @Autowired
    private lateinit var boardMembers: BoardMemberService

    @Test
    fun `the next board is Kandi until its first day, when it serves`() {
        val start = LocalDate.now().plusDays(10)
        val candidate = createUserWithRole(Role.MEMBER)
        addBoardMember(createBoardFixture(startDate = start), candidate)

        assertThat(boardMembers.candidatesOn(start.minusDays(1))).contains(candidate.id)
        assertThat(boardMembers.servingOn(start.minusDays(1))).doesNotContain(candidate.id)
        assertThat(boardMembers.candidatesOn(start)).doesNotContain(candidate.id)
        assertThat(boardMembers.servingOn(start)).contains(candidate.id)
    }
}
