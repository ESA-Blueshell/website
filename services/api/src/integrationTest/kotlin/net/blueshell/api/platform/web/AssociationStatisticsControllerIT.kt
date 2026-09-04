package net.blueshell.api.platform.web

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit

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
        val before = read("committees")

        createCommitteeFixture(name = "Committee One")
        createCommitteeFixture(name = "Committee Two")

        assertThat(read("committees")).isEqualTo(before + 2)
    }

    /**
     * The event count is the caller's own, by the same rule the events list follows.
     *
     * A visitor counts the approved events; the draft beside it is not one they could open,
     * so two events arrive and the number moves by one.
     */
    @Test
    fun `a visitor counts the events a visitor may see`() {
        val committee = createCommitteeFixture()
        val before = read("eventsLastYear")

        eventLastMonth(committee, "Approved", approved = true)
        eventLastMonth(committee, "Draft", approved = false)

        assertThat(read("eventsLastYear")).isEqualTo(before + 1)
    }

    /** The year counted is the one behind us, so an event that has not happened is not in it. */
    @Test
    fun `an event still to come is not one of the last year's`() {
        val committee = createCommitteeFixture()
        val before = read("eventsLastYear")

        eventAt(committee, "Next Month", Instant.now().plus(30, ChronoUnit.DAYS), approved = true)

        assertThat(read("eventsLastYear")).isEqualTo(before)
    }

    private fun eventLastMonth(committee: Committee, title: String, approved: Boolean): Event =
        eventAt(committee, title, Instant.now().minus(30, ChronoUnit.DAYS), approved)

    private fun eventAt(committee: Committee, title: String, start: Instant, approved: Boolean): Event =
        persist(
            Event(
                committee = committee,
                title = "$title ${System.nanoTime()}",
                description = "Event description",
                location = "Campus",
                startTime = start,
                endTime = start.plus(2, ChronoUnit.HOURS),
                approved = approved,
                membersOnly = false,
                signUp = false,
            )
        )

    /**
     * Read one number, as a visitor.
     *
     * A difference rather than a total: these run against a database the whole suite shares,
     * and what else is in it is not this test's business.
     */
    private fun read(number: String): Long {
        val answered = mvc.perform(get("/statistics/association"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsByteArray
        return mapper.readTree(answered).path(number).asLong()
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
