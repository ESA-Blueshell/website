package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.CohortMembershipSyncService
import net.blueshell.api.cohort.domain.CohortRemediation
import net.blueshell.api.cohort.domain.SyncCohortMembershipIntent
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
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

/** Without a bot no Discord role is made or linked, and the writes and reconciles of one linked before skip rather than fail. */
@SpringBootTest
class DiscordTargetIT : UserTestSupport() {
    @Autowired
    private lateinit var cohorts: CohortRepository

    @Autowired
    private lateinit var targets: TargetRepository

    @Autowired
    private lateinit var membership: CohortMembershipSyncService

    @Autowired
    private lateinit var remediation: CohortRemediation

    @Test
    fun `without a bot a role is neither made nor linked, and a linked one's sync and reconcile report Discord absent`() {
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
            ).andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("TargetSystemUnavailable"))
        mvc
            .perform(get("/management/cohort-targets/{system}", "DISCORD").with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))

        // A role linked while the bot was there.
        val role = targets.save(Target("DISCORD", TargetKind.ROLE, "Sitecie", cohortId = cohort.id, externalId = "123456789012345678"))
        assertThat(membership.sync(board.id!!, role.id!!, SyncCohortMembershipIntent.ADD)).contains("Discord cannot be reached")
        assertThat(remediation.verifyTarget(role.id!!, JobTrigger.BY_HAND)).contains("Discord cannot be reached")
    }
}
