package net.blueshell.api.platform.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class AssociationStatisticsControllerIT : UserTestSupport() {

    /** The pages that draw these have not persuaded anybody to log in yet. */
    @Test
    fun `a visitor who is not logged in gets the association's numbers`() {
        mvc.perform(get("/statistics/association"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.gamesPlayed").isNumber)
            .andExpect(jsonPath("$.seasonsPlayed").isNumber)
            .andExpect(jsonPath("$.committees").isNumber)
            .andExpect(jsonPath("$.boards").isNumber)
            .andExpect(jsonPath("$.teamsThisSeason").isNumber)
            .andExpect(jsonPath("$.eventsLastYear").isNumber)
    }

    /** There is no member count, and adding one later should have to be a decision. */
    @Test
    fun `the numbers say nothing about members`() {
        mvc.perform(get("/statistics/association"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members").doesNotExist())
            .andExpect(jsonPath("$.memberships").doesNotExist())
    }

    @Test
    fun `the committees counted are the committees that run`() {
        createCommitteeFixture(name = "Committee One")
        createCommitteeFixture(name = "Committee Two")

        mvc.perform(get("/statistics/association"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.committees").value(2))
    }

    /**
     * The event count is the caller's own, by the same rule the events list follows.
     *
     * A visitor counts the approved events; the draft beside it is not one they could open.
     */
    @Test
    fun `a visitor counts the events a visitor may see`() {
        val committee = createCommitteeFixture()
        createEventFixture(committee = committee, approved = true)
        createEventFixture(committee = committee, approved = false)

        mvc.perform(get("/statistics/association"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.eventsLastYear").value(1))
    }

    /** Opening the numbers up did not open up what they are counted from. */
    @Test
    fun `reading members is still refused`() {
        mvc.perform(get("/users"))
            .andExpect(status().is4xxClientError)
    }

    @Test
    fun `a board member reads the same endpoint`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(get("/statistics/association").with(bearer(board)))
            .andExpect(status().isOk)
    }
}
