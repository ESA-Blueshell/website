package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.CohortMembershipSyncService
import net.blueshell.api.cohort.domain.CohortRemediation
import net.blueshell.api.cohort.domain.SyncCohortMembershipIntent
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/** Without a bot a Discord role links by hand, and its writes and reconciles skip rather than fail. */
@SpringBootTest
class DiscordTargetIT : UserTestSupport() {
    @Autowired
    private lateinit var teamService: net.blueshell.api.esports.domain.TeamService

    @Autowired
    private lateinit var cohorts: CohortRepository

    @Autowired
    private lateinit var targets: TargetRepository

    @Autowired
    private lateinit var membership: CohortMembershipSyncService

    @Autowired
    private lateinit var remediation: CohortRemediation

    @Test
    fun `a role links by hand, cannot be made without a bot, and its sync and reconcile report Discord absent`() {
        val board = createUserWithRole(Role.BOARD)
        val cohort = cohorts.save(Cohort(type = CohortType.COMMITTEE_MEMBERS, label = "Discord ${UUID.randomUUID()}"))

        mvc
            .perform(
                post("/management/cohorts/{id}/targets/new", cohort.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"system":"DISCORD","label":"Sitecie"}""")
                    .with(signedIn(board)),
            ).andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("TargetSystemUnavailable"))
        mvc
            .perform(
                post("/management/cohorts/{id}/targets/existing", cohort.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"system":"DISCORD","externalId":"123456789012345678"}""")
                    .with(signedIn(board)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.kind").value("ROLE"))
        mvc
            .perform(get("/management/cohort-targets/{system}", "DISCORD").with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))

        val role = targets.findByCohortIdAndSystem(cohort.id!!, "DISCORD")!!
        assertThat(membership.sync(board.id!!, role.id!!, SyncCohortMembershipIntent.ADD)).contains("Discord cannot be reached")
        assertThat(remediation.verifyTarget(role.id!!, JobTrigger.BY_HAND)).contains("Discord cannot be reached")
    }

    @Test
    fun `a committee's Discord reads as absent without a bot, and setting it is refused for the board and the members`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)

        mvc
            .perform(get("/management/committees/{id}/discord", 1).with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.available").value(false))
        mvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .put("/management/committees/{id}/discord", 1)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"createRole":true,"createChannel":"sitecie"}""")
                    .with(signedIn(board)),
            ).andExpect(status().isServiceUnavailable)
        mvc.perform(get("/management/discord/roles").with(signedIn(board))).andExpect(jsonPath("$.length()").value(0))
        mvc.perform(get("/management/discord/channels").with(signedIn(board))).andExpect(jsonPath("$.length()").value(0))
        mvc.perform(get("/management/committees/{id}/discord", 1).with(signedIn(member))).andExpect(status().isForbidden)
        mvc
            .perform(get("/management/teams/{id}/discord", 1).with(signedIn(board)))
            .andExpect(jsonPath("$.available").value(false))
        mvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .delete(
                        "/management/teams/{id}/discord",
                        1,
                    ).with(signedIn(board)),
            ).andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `a team is archived and brought back, its cohort emptied and refilled without a bot to move its channel`() {
        val board = createUserWithRole(Role.BOARD)
        val team =
            teamService.create(
                net.blueshell.api.esports.domain
                    .TeamInput("Archive ${UUID.randomUUID().toString().take(6)}", null),
            )

        mvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .put("/esports/teams/{id}/archived", team.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"archived":true}""")
                    .with(signedIn(board)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.archived").value(true))
        mvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .put("/esports/teams/{id}/archived", team.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"archived":false}""")
                    .with(signedIn(board)),
            ).andExpect(jsonPath("$.archived").value(false))
    }
}
