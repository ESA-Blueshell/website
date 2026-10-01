package net.blueshell.api.contribution.web

import net.blueshell.api.contribution.domain.ContributionJobs
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
class IncassoRunIT : UserTestSupport() {
    private fun onIncasso(
        board: User,
        iban: String?,
    ): User {
        val member = createUserWithRole(Role.MEMBER)
        val membership = createMembershipFixture(member, startDate = LocalDate.now().minusMonths(2))
        if (iban != null) {
            mvc
                .perform(
                    put("/memberships/${membership.id}/mandate")
                        .with(signedIn(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"iban":"$iban","accountHolder":"${member.fullName}","signedOn":"${LocalDate.now().minusDays(3)}"}"""),
                ).andExpect(status().isOk)
        } else {
            transactionTemplate.execute { entityManager.find(membership.javaClass, membership.id).incasso = true }
        }
        return member
    }

    @Test
    fun `the board collects from members with a mandate, tells each of them, and the run waits for ING`() {
        val board = createUserWithRole(Role.BOARD)
        val period = createContributionPeriodFixture()
        val withMandate = onIncasso(board, "NL91ABNA0417164300")
        val without = onIncasso(board, null)
        val before = findJobsByType(ContributionJobs.IncassoNotification.type).size

        mvc
            .perform(get("/contributionPeriods/${period.id}/incasso").with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.userId == ${withMandate.id})].ibanLastFour").value("4300"))
            .andExpect(jsonPath("$[?(@.userId == ${without.id})].leftOut").value("NO_BANK_DETAILS"))

        val collectionDate = LocalDate.now().plusDays(7)
        val answer =
            mvc
                .perform(
                    post("/contributionPeriods/${period.id}/incassoRuns")
                        .with(signedIn(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """{"userIds":[${withMandate.id}],"collectionDate":"$collectionDate","statementText":"Contributie"}""",
                        ),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.collections[0].ibanLastFour").value("4300"))
                .andExpect(jsonPath("$.collections[0].mandateReference").isNotEmpty)
                .andExpect(jsonPath("$.submittedAt").doesNotExist())
                .andReturn()
                .response.contentAsString
        assertThat(answer).doesNotContain("0417")
        val runId = Regex("\"id\":(\\d+)").find(answer)!!.groupValues[1]

        assertThat(findJobsByType(ContributionJobs.IncassoNotification.type)).hasSize(before + 1)
        mvc
            .perform(get("/incassoRuns/$runId").with(signedIn(board)))
            .andExpect(jsonPath("$.collectionDate").value(collectionDate.toString()))
        mvc
            .perform(get("/contributionPeriods/${period.id}/members").with(signedIn(board)))
            .andExpect(jsonPath("$.incassoRuns[0].id").value(runId.toLong()))
            .andExpect(jsonPath("$.incassoRuns[0].collections").value(1))
            .andExpect(jsonPath("$.incassoRuns[0].submittedAt").doesNotExist())
            .andExpect(jsonPath("$.members[?(@.userId == ${withMandate.id})].lastEmailKind").value("INCASSO_NOTIFICATION"))

        mvc
            .perform(
                post("/contributionPeriods/${period.id}/incassoRuns")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userIds":[${without.id}],"collectionDate":"$collectionDate","statementText":"x"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("NotCollectable"))
    }

    @Test
    fun `members cannot plan or start a run`() {
        val member = createUserWithRole(Role.MEMBER)
        val period = createContributionPeriodFixture()
        mvc.perform(get("/contributionPeriods/${period.id}/incasso").with(signedIn(member))).andExpect(status().isForbidden)
        mvc
            .perform(
                post("/contributionPeriods/${period.id}/incassoRuns")
                    .with(signedIn(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userIds":[1],"collectionDate":"${LocalDate.now().plusDays(7)}"}"""),
            ).andExpect(status().isForbidden)
    }
}
