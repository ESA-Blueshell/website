package net.blueshell.api.contribution.web

import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
class MemberContributionIT : UserTestSupport() {
    @Test
    fun `a member reads only their own contributions, and nobody signed out reads any`() {
        val member = createUserWithRole(Role.MEMBER)
        createMembershipFixture(user = member)
        val period = createContributionPeriodFixture()
        val stranger = createUserWithRole(Role.GUEST)

        mvc
            .perform(get("/users/me/contributions").with(signedIn(member)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.held").value(true))
            .andExpect(jsonPath("$.periods[0].periodId").value(period.id!!))
        mvc
            .perform(get("/users/me/contributions").with(signedIn(stranger)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.held").value(false))
            .andExpect(jsonPath("$.periods").isEmpty)
        mvc.perform(get("/users/me/contributions")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a member reads only their own payment emails and payments`() {
        val member = createUserWithRole(Role.MEMBER)
        val other = createUserWithRole(Role.MEMBER)
        createMembershipFixture(user = member)
        createMembershipFixture(user = other)
        val period = createContributionPeriodFixture()
        val due = LocalDate.now().plusWeeks(2)
        persist(
            ContributionReminder(user = member, contributionPeriod = period).apply {
                askedAt = Instant.now().minusSeconds(3600)
                paymentDueDate = due
            },
        )
        persist(
            IncassoNotification(
                user = other,
                contributionPeriod = period,
                feeType = BulkFeeType.FULL_YEAR_FEE,
                amount = 30.0,
                debitDate = due.plusDays(5),
            ),
        )
        persist(Contribution(id = Contribution.Id(), user = other, contributionPeriod = period))

        mvc
            .perform(get("/users/me/contributions").with(signedIn(member)))
            .andExpect(jsonPath("$.periods[0].lastEmailKind").value("REMINDER"))
            .andExpect(jsonPath("$.periods[0].dueDate").value(due.toString()))
            .andExpect(jsonPath("$.periods[0].paidAt").doesNotExist())
        mvc
            .perform(get("/users/me/contributions").with(signedIn(other)))
            .andExpect(jsonPath("$.periods[0].lastEmailKind").value("INCASSO_NOTIFICATION"))
            .andExpect(jsonPath("$.periods[0].paidAt").isNotEmpty)
    }

    @Test
    fun `a signed-in person reads the account to transfer to`() {
        mvc
            .perform(get("/contributions/bank-account").with(signedIn(createUserWithRole(Role.GUEST))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.iban").isNotEmpty)
        mvc.perform(get("/contributions/bank-account")).andExpect(status().isUnauthorized)
    }
}
