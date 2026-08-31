package net.blueshell.api.contribution.web

import net.blueshell.api.contribution.domain.ContributionReminderEmailJob
import net.blueshell.api.contribution.domain.IncassoNotificationEmailJob
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The fee cycle end to end: what the preview says, what the send does, and that the two
 * agree.
 */
@SpringBootTest
class FeeCycleControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var reminderRepository: ContributionReminderRepository

    @Autowired
    private lateinit var preNotificationRepository: IncassoNotificationRepository

    @Autowired
    private lateinit var reminderEmailJob: ContributionReminderEmailJob

    @Autowired
    private lateinit var preNotificationEmailJob: IncassoNotificationEmailJob

    @Autowired
    private lateinit var jsonMapper: ObjectMapper

    private val periodStart = LocalDate.now().minusMonths(2)
    private val periodEnd = LocalDate.now().plusMonths(6)

    /** The label the emails carry, derived here so the assertions do not pin a calendar year. */
    private val academicYear =
        if (periodEnd.year > periodStart.year) "${periodStart.year}/${periodEnd.year}" else "${periodStart.year}"

    private val cutoff = LocalDate.now().minusDays(10)
    private val dueDate = LocalDate.now().plusMonths(1)
    private val debitDate = LocalDate.now().plusMonths(1).plusDays(14)

    private fun period(): ContributionPeriod = contributionFactory.createPeriod(
        startDate = periodStart,
        endDate = periodEnd,
        halfYearCutoffDate = cutoff,
    )

    /** A member of the period, on the side of the partition their flag puts them on. */
    private fun member(
        incasso: Boolean,
        memberType: MemberType = MemberType.REGULAR,
        startDate: LocalDate = LocalDate.now().minusMonths(1),
        email: String? = null,
    ): User {
        val user = userFactory.createUserWithRole(Role.MEMBER)
        if (email != null) {
            user.email = email
            persist(user)
        }
        userFactory.createMembership(user, memberType = memberType, startDate = startDate, incasso = incasso)
        return user
    }

    private fun sendBody(
        periodId: Long?,
        feeTypeOverrides: Map<Long?, BulkFeeType> = emptyMap(),
    ): String = jsonMapper.writeValueAsString(
        mapOf(
            "contributionPeriodId" to periodId,
            "paymentDueDate" to dueDate.toString(),
            "debitDate" to debitDate.toString(),
            "feeTypeOverrides" to feeTypeOverrides.mapKeys { it.key.toString() },
        ),
    )

    private fun previewEmail(board: User, periodId: Long?, userId: Long?, feeType: BulkFeeType? = null) =
        mvc.perform(
            get("/contributions/fee-cycle/email-preview")
                .with(bearer(board))
                .param("contributionPeriodId", "$periodId")
                .param("userId", "$userId")
                .param("paymentDueDate", dueDate.toString())
                .param("debitDate", debitDate.toString())
                .apply { if (feeType != null) param("feeType", feeType.name) },
        )

    private fun preview(board: User, periodId: Long?) =
        mvc.perform(get("/contributions/fee-cycle").with(bearer(board)).param("contributionPeriodId", "$periodId"))

    private fun send(board: User, body: String) =
        mvc.perform(
            post("/contributions/fee-cycle/send")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )

    @Nested
    inner class ThePreview {

        @Test
        fun `lists every unpaid member of the period, split by the direct-debit flag`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onDirectDebit = member(incasso = true)
            val onTransfer = member(incasso = false)

            preview(board, period.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.contributionPeriodId").value(period.id))
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[?(@.userId == ${onDirectDebit.id})].group").value("DIRECT_DEBIT"))
                .andExpect(jsonPath("$.rows[?(@.userId == ${onTransfer.id})].group").value("TRANSFER"))
        }

        @Test
        fun `leaves out a member who has already paid`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val paid = member(incasso = false)
            val unpaid = member(incasso = false)
            persist(Contribution(id = Contribution.Id(paid.id, period.id), user = paid, contributionPeriod = period))

            preview(board, period.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].userId").value(unpaid.id))
        }

        @Test
        fun `shows an honorary member excluded, owing nothing`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val honorary = member(incasso = false, memberType = MemberType.HONORARY)

            preview(board, period.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows[?(@.userId == ${honorary.id})].disposition").value("EXCLUDED"))
                .andExpect(jsonPath("$.rows[?(@.userId == ${honorary.id})].reason").value("HONORARY"))
                .andExpect(jsonPath("$.rows[?(@.userId == ${honorary.id})].feeType").isEmpty)
        }

        @Test
        fun `shows a member with no email address excluded`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val unreachable = member(incasso = false, email = "")

            preview(board, period.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows[?(@.userId == ${unreachable.id})].disposition").value("EXCLUDED"))
                .andExpect(jsonPath("$.rows[?(@.userId == ${unreachable.id})].reason").value("NO_EMAIL"))
        }

        @Test
        fun `prices each row from the period, by the cutoff the period carries`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val startedOnTheCutoff = member(incasso = false, startDate = cutoff)
            val startedAfterIt = member(incasso = false, startDate = cutoff.plusDays(1))

            preview(board, period.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows[?(@.userId == ${startedOnTheCutoff.id})].feeType").value("FULL_YEAR_FEE"))
                .andExpect(jsonPath("$.rows[?(@.userId == ${startedOnTheCutoff.id})].amount").value(45.0))
                .andExpect(jsonPath("$.rows[?(@.userId == ${startedAfterIt.id})].feeType").value("HALF_YEAR_FEE"))
                .andExpect(jsonPath("$.rows[?(@.userId == ${startedAfterIt.id})].amount").value(25.0))
        }

        /**
         * The flag on a membership that has ended is not how the member pays now, and
         * sending the wrong statement on a stale flag costs them money.
         */
        @Test
        fun `partitions by the active membership, not one that has ended`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val switched = userFactory.createUserWithRole(Role.MEMBER)
            userFactory.createMembership(
                switched,
                startDate = LocalDate.now().minusMonths(2),
                endDate = LocalDate.now().minusDays(20),
                incasso = true,
            )
            userFactory.createMembership(switched, startDate = LocalDate.now().minusDays(19), incasso = false)

            preview(board, period.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows[?(@.userId == ${switched.id})].group").value("TRANSFER"))
        }

        @Test
        fun `is refused to a member`() {
            val member = userFactory.createUserWithRole(Role.MEMBER)
            preview(member, period().id).andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class TheSend {

        @Test
        fun `writes to both sides from one confirmation and reports each separately`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onDirectDebit = member(incasso = true)
            val onTransfer = member(incasso = false)

            send(board, sendBody(period.id))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.paymentRequestsQueued").value(1))
                .andExpect(jsonPath("$.preNotificationsQueued").value(1))
                .andExpect(jsonPath("$.excluded").value(0))

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!).map { it.userId })
                .containsExactly(onTransfer.id)
            assertThat(preNotificationRepository.findByContributionPeriod_Id(period.id!!).map { it.userId })
                .containsExactly(onDirectDebit.id)
        }

        @Test
        fun `queues one email per member rather than one request per member`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            member(incasso = false)
            member(incasso = false)
            member(incasso = true)

            send(board, sendBody(period.id)).andExpect(status().isOk)

            assertThat(findJobsByType(EmailJobs.ContributionReminder.type)).hasSize(2)
            assertThat(findJobsByType(EmailJobs.IncassoNotification.type)).hasSize(1)
        }

        @Test
        fun `writes to nobody the preview excluded`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            member(incasso = false, memberType = MemberType.HONORARY)
            member(incasso = true, email = "")

            send(board, sendBody(period.id))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.paymentRequestsQueued").value(0))
                .andExpect(jsonPath("$.preNotificationsQueued").value(0))
                .andExpect(jsonPath("$.excluded").value(2))
        }

        /**
         * The acceptance criterion behind sharing one plan: the send writes to exactly the
         * members the preview said it would, on the side the preview put them on.
         */
        @Test
        fun `writes to exactly the members the preview said it would`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            member(incasso = true)
            member(incasso = true, memberType = MemberType.ALUMNI)
            member(incasso = false)
            member(incasso = false, memberType = MemberType.HONORARY)
            member(incasso = false, email = "")

            val previewed = jsonMapper.readTree(
                preview(board, period.id).andExpect(status().isOk).andReturn().response.contentAsString,
            )["rows"]
            val expectedTransfer = previewed
                .filter { it["group"].asString() == "TRANSFER" && it["disposition"].asString() == "INCLUDED" }
                .map { it["userId"].asLong() }
            val expectedDirectDebit = previewed
                .filter { it["group"].asString() == "DIRECT_DEBIT" && it["disposition"].asString() == "INCLUDED" }
                .map { it["userId"].asLong() }

            send(board, sendBody(period.id))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.paymentRequestsQueued").value(expectedTransfer.size))
                .andExpect(jsonPath("$.preNotificationsQueued").value(expectedDirectDebit.size))

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!).map { it.userId })
                .containsExactlyInAnyOrderElementsOf(expectedTransfer)
            assertThat(preNotificationRepository.findByContributionPeriod_Id(period.id!!).map { it.userId })
                .containsExactlyInAnyOrderElementsOf(expectedDirectDebit)
        }

        /**
         * The period's fees are editable, so a record that only stored the type would let a
         * change of next year's fee rewrite what last year's email is recorded as saying.
         */
        @Test
        fun `records the amount it stated, not only the type`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            member(incasso = false, startDate = cutoff.plusDays(1))
            member(incasso = true, startDate = cutoff)

            send(board, sendBody(period.id)).andExpect(status().isOk)

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!).single().amount)
                .isEqualTo(25.0)
            assertThat(preNotificationRepository.findByContributionPeriod_Id(period.id!!).single().amount)
                .isEqualTo(45.0)
        }

        @Test
        fun `records what each statement said, so the email quotes the true reason`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onTransfer = member(incasso = false, startDate = cutoff.plusDays(1))
            val onDirectDebit = member(incasso = true, memberType = MemberType.ALUMNI)

            send(board, sendBody(period.id)).andExpect(status().isOk)

            val reminder = reminderRepository.findByContributionPeriod_Id(period.id!!).single()
            assertThat(reminder.userId).isEqualTo(onTransfer.id)
            assertThat(reminder.feeType).isEqualTo(BulkFeeType.HALF_YEAR_FEE)
            assertThat(reminder.paymentDueDate).isEqualTo(dueDate)

            val preNotification = preNotificationRepository.findByContributionPeriod_Id(period.id!!).single()
            assertThat(preNotification.userId).isEqualTo(onDirectDebit.id)
            assertThat(preNotification.feeType).isEqualTo(BulkFeeType.ALUMNI_FEE)
            assertThat(preNotification.debitDate).isEqualTo(debitDate)
        }

        /**
         * The treasurer chases, and may run the cycle over a period as often as they need.
         * Each run is its own ask, so a member asked three times reads as three asks rather
         * than one row that only remembers the last.
         */
        @Test
        fun `asking again writes another ask, as often as it is done`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onTransfer = member(incasso = false)

            send(board, sendBody(period.id)).andExpect(status().isOk)
            send(board, sendBody(period.id, mapOf(onTransfer.id to BulkFeeType.ALUMNI_FEE)))
                .andExpect(status().isOk)
            send(board, sendBody(period.id)).andExpect(status().isOk)

            val asks = reminderRepository.findByContributionPeriod_Id(period.id!!)
            assertThat(asks).hasSize(3)
            assertThat(asks.map { it.userId }.distinct()).containsExactly(onTransfer.id)
            assertThat(asks.map { it.feeType })
                .containsExactly(BulkFeeType.FULL_YEAR_FEE, BulkFeeType.ALUMNI_FEE, BulkFeeType.FULL_YEAR_FEE)
        }

        @Test
        fun `notifying again writes another pre-notification, so a moved debit date can be re-told`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            member(incasso = true)

            send(board, sendBody(period.id)).andExpect(status().isOk)
            send(board, sendBody(period.id)).andExpect(status().isOk)

            assertThat(preNotificationRepository.findByContributionPeriod_Id(period.id!!)).hasSize(2)
        }

        @Test
        fun `each ask queues its own email`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            member(incasso = false)

            send(board, sendBody(period.id)).andExpect(status().isOk)
            send(board, sendBody(period.id)).andExpect(status().isOk)

            assertThat(findJobsByType(EmailJobs.ContributionReminder.type)).hasSize(2)
        }

        @Test
        fun `shows the most recent of a member's asks`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onTransfer = member(incasso = false)
            persist(
                ContributionReminder(
                    user = onTransfer,
                    contributionPeriod = period,
                    askedAt = LocalDate.now().minusMonths(3).atStartOfDay().toInstant(ZoneOffset.UTC),
                ),
            )

            send(board, sendBody(period.id)).andExpect(status().isOk)

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).hasSize(2)
            preview(board, period.id)
                .andExpect(status().isOk)
                .andExpect(
                    jsonPath("$.rows[?(@.userId == ${onTransfer.id})].lastAskedOn")
                        .value(LocalDate.now().toString()),
                )
        }

        @Test
        fun `shows when a member was last asked, on their side of the partition`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onTransfer = member(incasso = false)
            val onDirectDebit = member(incasso = true)

            send(board, sendBody(period.id)).andExpect(status().isOk)

            val today = LocalDate.now().toString()
            preview(board, period.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows[?(@.userId == ${onTransfer.id})].lastAskedOn").value(today))
                .andExpect(jsonPath("$.rows[?(@.userId == ${onDirectDebit.id})].lastAskedOn").value(today))
        }

        @Test
        fun `refuses a fee type naming a member the cycle does not write to`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            member(incasso = false)
            val honorary = member(incasso = false, memberType = MemberType.HONORARY)

            send(board, sendBody(period.id, mapOf(honorary.id to BulkFeeType.ALUMNI_FEE)))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.errors[0].code").value("NonRecipientFeeTypeUserIds"))
                .andExpect(jsonPath("$.errors[0].values[0]").value(honorary.id))

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).isEmpty()
        }

        @Test
        fun `is refused to a member`() {
            val member = userFactory.createUserWithRole(Role.MEMBER)
            send(member, sendBody(period().id)).andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class TheEmailsThatGoOut {

        @Test
        fun `the payment request states the amount, the reason and the due date`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onTransfer = member(incasso = false, startDate = cutoff.plusDays(1))

            send(board, sendBody(period.id)).andExpect(status().isOk)
            emailTransportClient.reset()
            val ask = reminderRepository.findByContributionPeriod_Id(period.id!!).single()
            reminderEmailJob.handle(
                jsonMapper.writeValueAsString(EmailJobs.ContributionReminderPayload(ask.id!!)),
            )

            val sent = emailTransportClient.sentEmails.single()
            assertThat(sent.toEmail).isEqualTo(onTransfer.email)
            assertThat(sent.htmlContent)
                .contains("25,00")
                .contains("the half-year fee")
                .contains(dueDate.dayOfMonth.toString())
        }

        @Test
        fun `the pre-notification states the amount, the reason and the debit date`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onDirectDebit = member(incasso = true, startDate = cutoff)

            send(board, sendBody(period.id)).andExpect(status().isOk)
            emailTransportClient.reset()
            val notification = preNotificationRepository.findByContributionPeriod_Id(period.id!!).single()
            preNotificationEmailJob.handle(
                jsonMapper.writeValueAsString(EmailJobs.IncassoNotificationPayload(notification.id!!)),
            )

            val sent = emailTransportClient.sentEmails.single()
            assertThat(sent.toEmail).isEqualTo(onDirectDebit.email)
            assertThat(sent.htmlContent)
                .contains("45,00")
                .contains("the full-year fee")
                .contains(debitDate.dayOfMonth.toString())
                .doesNotContain("Bank transfer")
        }
    }

    /**
     * Reading one member's email before sending to a hundred. It renders through the shared
     * preview renderer from the same `EmailContent` the send builds, which is what stops the
     * two drifting.
     */
    @Nested
    inner class ReadingOneMembersEmail {

        @Test
        fun `a transfer member reads the payment request`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onTransfer = member(incasso = false, startDate = cutoff.plusDays(1))

            previewEmail(board, period.id, onTransfer.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.group").value("TRANSFER"))
                .andExpect(jsonPath("$.feeType").value("HALF_YEAR_FEE"))
                .andExpect(jsonPath("$.recipientEmail").value(onTransfer.email))
                .andExpect(jsonPath("$.subject").value("Please pay your Blueshell contribution ($academicYear)"))
        }

        @Test
        fun `a direct-debit member reads the pre-notification`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onDirectDebit = member(incasso = true, memberType = MemberType.ALUMNI)

            previewEmail(board, period.id, onDirectDebit.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.group").value("DIRECT_DEBIT"))
                .andExpect(jsonPath("$.feeType").value("ALUMNI_FEE"))
                .andExpect(
                    jsonPath("$.subject")
                        .value("Your Blueshell contribution will be collected automatically ($academicYear)"),
                )
        }

        // The hosted images are inlined so the pictures show in a browser that cannot reach
        // the asset host — which is what going through the shared renderer buys.
        @Test
        fun `the rendered body carries the amount, the reason and the images`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onTransfer = member(incasso = false, startDate = cutoff)

            val body = previewEmail(board, period.id, onTransfer.id)
                .andExpect(status().isOk)
                .andReturn().response.contentAsString
            val html = jsonMapper.readTree(body)["html"].asString()

            assertThat(html)
                .contains("45,00")
                .contains("the full-year fee")
                .contains("data:image/png;base64,")
        }

        @Test
        fun `an overridden fee type is what the email quotes`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onTransfer = member(incasso = false, startDate = cutoff)

            previewEmail(board, period.id, onTransfer.id, BulkFeeType.ALUMNI_FEE)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.feeType").value("ALUMNI_FEE"))
        }

        @Test
        fun `writes no record and queues no email`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onTransfer = member(incasso = false)

            previewEmail(board, period.id, onTransfer.id).andExpect(status().isOk)

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).isEmpty()
            assertThat(preNotificationRepository.findByContributionPeriod_Id(period.id!!)).isEmpty()
            assertThat(findJobsByType(EmailJobs.ContributionReminder.type)).isEmpty()
            assertThat(findJobsByType(EmailJobs.IncassoNotification.type)).isEmpty()
        }

        @Test
        fun `refuses a member the cycle sends nothing to`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val honorary = member(incasso = false, memberType = MemberType.HONORARY)

            previewEmail(board, period.id, honorary.id).andExpect(status().isNotFound)
        }

        @Test
        fun `refuses a member the cycle is not about`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val stranger = userFactory.createUserWithRole(Role.MEMBER)

            previewEmail(board, period.id, stranger.id).andExpect(status().isNotFound)
        }

        @Test
        fun `is refused to a member`() {
            val period = period()
            val member = member(incasso = false)

            previewEmail(member, period.id, member.id).andExpect(status().isForbidden)
        }
    }
}