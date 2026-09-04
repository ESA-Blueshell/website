package net.blueshell.api.contribution.web

import net.blueshell.api.contribution.domain.ContributionEmailKind
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.Role
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
import java.time.Instant
import java.time.LocalDate

/** The payment emails end to end, and that the table and the send agree. */
@SpringBootTest
class BulkContributionEmailControllerIT : UserTestSupport() {

    private companion object {
        /** An id no user has, for the refusals that are about the selection being stale. */
        const val GONE = 9_999_999L
    }

    @Autowired
    private lateinit var reminderRepository: ContributionReminderRepository

    @Autowired
    private lateinit var preNotificationRepository: IncassoNotificationRepository

    @Autowired
    private lateinit var jsonMapper: ObjectMapper

    private val periodStart = LocalDate.now().minusMonths(2)
    private val periodEnd = LocalDate.now().plusMonths(6)
    private val cutoff = LocalDate.now().minusDays(10)
    private val dueDate = LocalDate.now().plusMonths(1)
    private val debitDate = LocalDate.now().plusMonths(1).plusDays(14)

    private fun period(): ContributionPeriod = contributionFactory.createPeriod(
        startDate = periodStart,
        endDate = periodEnd,
        halfYearCutoffDate = cutoff,
    )

    private fun member(
        incasso: Boolean,
        memberType: MemberType = MemberType.REGULAR,
        startDate: LocalDate = LocalDate.now().minusMonths(1),
        endDate: LocalDate? = null,
        email: String? = null,
    ): User {
        val user = userFactory.createUserWithRole(Role.MEMBER)
        if (email != null) {
            user.email = email
            persist(user)
        }
        userFactory.createMembership(
            user,
            memberType = memberType,
            startDate = startDate,
            endDate = endDate,
            incasso = incasso,
        )
        return user
    }

    private fun preview(board: User, periodId: Long?, vararg userIds: Long?) = mvc.perform(
        post("/contributions/bulk/email/preview")
            .with(bearer(board))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                jsonMapper.writeValueAsString(
                    mapOf("contributionPeriodId" to periodId, "userIds" to userIds.toList()),
                ),
            ),
    )

    private fun sendBody(
        periodId: Long?,
        userIds: List<Long?>,
        forciblyIncluded: List<Long?> = emptyList(),
        kindOverrides: Map<Long?, ContributionEmailKind> = emptyMap(),
        feeTypeOverrides: Map<Long?, BulkFeeType> = emptyMap(),
        paymentDueDate: LocalDate? = dueDate,
        debitDate: LocalDate? = this.debitDate,
    ): String = jsonMapper.writeValueAsString(
        mapOf(
            "contributionPeriodId" to periodId,
            "userIds" to userIds,
            "forciblyIncludedUserIds" to forciblyIncluded,
            "kindOverrides" to kindOverrides.mapKeys { it.key.toString() },
            "feeTypeOverrides" to feeTypeOverrides.mapKeys { it.key.toString() },
            "paymentDueDate" to paymentDueDate?.toString(),
            "debitDate" to debitDate?.toString(),
        ),
    )

    private fun send(board: User, body: String) = mvc.perform(
        post("/contributions/bulk/email/send")
            .with(bearer(board))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body),
    )

    private fun readEmail(
        board: User,
        kind: ContributionEmailKind,
        periodId: Long?,
        userId: Long?,
        feeType: BulkFeeType? = null,
    ) = mvc.perform(
        get("/contributions/bulk/email/message")
            .with(bearer(board))
            .param("kind", kind.name)
            .param("contributionPeriodId", "$periodId")
            .param("userId", "$userId")
            .param("date", dueDate.toString())
            .apply { if (feeType != null) param("feeType", feeType.name) },
    )

    @Nested
    inner class WhatTheTableSays {

        @Test
        fun `each selected member is routed by their direct-debit flag`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onDirectDebit = member(incasso = true)
            val onTransfer = member(incasso = false)

            preview(board, period.id, onDirectDebit.id, onTransfer.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.contributionPeriodId").value(period.id))
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(
                    jsonPath("$.rows[?(@.userId == ${onDirectDebit.id})].defaultKind")
                        .value("INCASSO_NOTIFICATION"),
                )
                .andExpect(jsonPath("$.rows[?(@.userId == ${onTransfer.id})].defaultKind").value("REMINDER"))
        }

        @Test
        fun `the table is exactly the selection`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val selected = member(incasso = false)
            member(incasso = false)

            preview(board, period.id, selected.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].userId").value(selected.id))
        }

        @Test
        fun `an id naming nobody is named rather than left out of the count`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val selected = member(incasso = false)

            preview(board, period.id, selected.id, 9_999_999L)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.unknownUserIds.length()").value(1))
                .andExpect(jsonPath("$.unknownUserIds[0]").value(9_999_999L))
        }

        @Test
        fun `an honorary member is hard-excluded, owing nothing`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val honorary = member(incasso = false, memberType = MemberType.HONORARY)

            preview(board, period.id, honorary.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows[0].disposition").value("EXCLUDED"))
                .andExpect(jsonPath("$.rows[0].reason").value("HONORARY"))
                .andExpect(jsonPath("$.rows[0].feeType").doesNotExist())
        }

        @Test
        fun `a member with no address on file is hard-excluded`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val unreachable = member(incasso = false, email = "")

            preview(board, period.id, unreachable.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows[0].disposition").value("EXCLUDED"))
                .andExpect(jsonPath("$.rows[0].reason").value("NO_EMAIL"))
        }

        @Test
        fun `a member who has paid is warned about rather than dropped`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val paid = member(incasso = false)
            persist(Contribution(id = Contribution.Id(paid.id, period.id), user = paid, contributionPeriod = period))

            preview(board, period.id, paid.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows[0].disposition").value("WARNING"))
                .andExpect(jsonPath("$.rows[0].reason").value("ALREADY_PAID"))
        }

        @Test
        fun `a member who held no membership during the period is warned about`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val former = member(
                incasso = false,
                startDate = periodStart.minusYears(3),
                endDate = periodStart.minusYears(2),
            )

            preview(board, period.id, former.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows[0].disposition").value("WARNING"))
                .andExpect(jsonPath("$.rows[0].reason").value("NOT_MEMBER_IN_PERIOD"))
        }

        @Test
        fun `each row is priced from the period, by the cutoff the period carries`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onTheCutoff = member(incasso = false, startDate = cutoff)
            val afterIt = member(incasso = false, startDate = cutoff.plusDays(1))

            preview(board, period.id, onTheCutoff.id, afterIt.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows[?(@.userId == ${onTheCutoff.id})].feeType").value("FULL_YEAR_FEE"))
                .andExpect(jsonPath("$.rows[?(@.userId == ${onTheCutoff.id})].amount").value(45.0))
                .andExpect(jsonPath("$.rows[?(@.userId == ${afterIt.id})].feeType").value("HALF_YEAR_FEE"))
                .andExpect(jsonPath("$.rows[?(@.userId == ${afterIt.id})].amount").value(25.0))
        }

        @Test
        fun `a row carries when each statement was last sent, separately`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val chased = member(incasso = true)
            persist(
                ContributionReminder(
                    user = chased,
                    contributionPeriod = period,
                    feeType = BulkFeeType.FULL_YEAR_FEE,
                    amount = 45.0,
                    paymentDueDate = dueDate,
                    askedAt = Instant.now().minusSeconds(86_400),
                ),
            )

            preview(board, period.id, chased.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows[0].lastRemindedOn").isNotEmpty)
                .andExpect(jsonPath("$.rows[0].lastNotifiedOn").doesNotExist())
        }
    }

    @Nested
    inner class WhatTheSendDoes {

        @Test
        fun `one confirmation writes both statements and reports each`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onDirectDebit = member(incasso = true)
            val onTransfer = member(incasso = false)

            send(board, sendBody(period.id, listOf(onDirectDebit.id, onTransfer.id)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.remindersSent").value(1))
                .andExpect(jsonPath("$.incassoNotificationsSent").value(1))
                .andExpect(jsonPath("$.notWrittenTo").value(0))

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!).map { it.userId })
                .containsExactly(onTransfer.id)
            assertThat(preNotificationRepository.findByContributionPeriod_Id(period.id!!).map { it.userId })
                .containsExactly(onDirectDebit.id)
        }

        @Test
        fun `a switched member gets the statement the treasurer chose`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onDirectDebit = member(incasso = true)

            send(
                board,
                sendBody(
                    period.id,
                    listOf(onDirectDebit.id),
                    kindOverrides = mapOf(onDirectDebit.id to ContributionEmailKind.REMINDER),
                ),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.remindersSent").value(1))
                .andExpect(jsonPath("$.incassoNotificationsSent").value(0))

            assertThat(preNotificationRepository.findByContributionPeriod_Id(period.id!!)).isEmpty()
        }

        @Test
        fun `a warned member is written to only once ticked back in`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val paid = member(incasso = false)
            persist(Contribution(id = Contribution.Id(paid.id, period.id), user = paid, contributionPeriod = period))

            send(board, sendBody(period.id, listOf(paid.id)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.remindersSent").value(0))
                .andExpect(jsonPath("$.notWrittenTo").value(1))

            send(board, sendBody(period.id, listOf(paid.id), forciblyIncluded = listOf(paid.id)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.remindersSent").value(1))
        }

        @Test
        fun `a hard-excluded member is not written to`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val honorary = member(incasso = false, memberType = MemberType.HONORARY)

            send(board, sendBody(period.id, listOf(honorary.id)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.remindersSent").value(0))
                .andExpect(jsonPath("$.notWrittenTo").value(1))

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).isEmpty()
        }

        @Test
        fun `sending twice records two asks rather than overwriting the first`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val chased = member(incasso = false)

            send(board, sendBody(period.id, listOf(chased.id))).andExpect(status().isOk)
            send(board, sendBody(period.id, listOf(chased.id))).andExpect(status().isOk)

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).hasSize(2)
        }

        @Test
        fun `an overridden fee type is what the record states`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val member = member(incasso = false)

            send(
                board,
                sendBody(period.id, listOf(member.id), feeTypeOverrides = mapOf(member.id to BulkFeeType.ALUMNI_FEE)),
            ).andExpect(status().isOk)

            val written = reminderRepository.findByContributionPeriod_Id(period.id!!).single()
            assertThat(written.feeType).isEqualTo(BulkFeeType.ALUMNI_FEE)
            assertThat(written.amount).isEqualTo(10.0)
        }
    }

    /**
     * Every refusal names the request field it is about and a stable code. Those field
     * names are what the wizard routes a refusal on, so they are asserted here rather than
     * left to the client to discover.
     */
    @Nested
    inner class WhenASendIsRefused {

        @Test
        fun `a fee type naming somebody the send skips refuses the whole thing`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val member = member(incasso = false)
            val honorary = member(incasso = false, memberType = MemberType.HONORARY)

            send(
                board,
                sendBody(
                    period.id,
                    listOf(member.id, honorary.id),
                    feeTypeOverrides = mapOf(honorary.id to BulkFeeType.ALUMNI_FEE),
                ),
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.errors[0].field").value("feeTypeOverrides"))
                .andExpect(jsonPath("$.errors[0].code").value("NonRecipientFeeTypeUserIds"))
                .andExpect(jsonPath("$.errors[0].values[0]").value(honorary.id))

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).isEmpty()
        }

        @Test
        fun `an email chosen for somebody the send skips refuses the whole thing`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val member = member(incasso = false)
            val honorary = member(incasso = false, memberType = MemberType.HONORARY)

            send(
                board,
                sendBody(
                    period.id,
                    listOf(member.id, honorary.id),
                    kindOverrides = mapOf(honorary.id to ContributionEmailKind.REMINDER),
                ),
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.errors[0].field").value("kindOverrides"))
                .andExpect(jsonPath("$.errors[0].code").value("NonRecipientEmailKindUserIds"))

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).isEmpty()
        }

        @Test
        fun `an id naming nobody is refused rather than dropped`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val member = member(incasso = false)

            send(board, sendBody(period.id, listOf(member.id, GONE)))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.errors[0].field").value("userIds"))
                .andExpect(jsonPath("$.errors[0].code").value("UnknownUserIds"))
                .andExpect(jsonPath("$.errors[0].values[0]").value(GONE))

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).isEmpty()
        }

        @Test
        fun `naming the same member twice is refused`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val member = member(incasso = false)

            send(board, sendBody(period.id, listOf(member.id, member.id)))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.errors[0].field").value("userIds"))
                .andExpect(jsonPath("$.errors[0].code").value("DuplicateUserIds"))
                .andExpect(jsonPath("$.errors[0].values[0]").value(member.id))

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).isEmpty()
        }

        @Test
        fun `ticking back in somebody the send still will not write to is refused`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val honorary = member(incasso = false, memberType = MemberType.HONORARY)

            send(board, sendBody(period.id, listOf(honorary.id), forciblyIncluded = listOf(honorary.id)))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.errors[0].field").value("forciblyIncludedUserIds"))
                .andExpect(jsonPath("$.errors[0].code").value("NonRecipientForcedUserIds"))
                .andExpect(jsonPath("$.errors[0].values[0]").value(honorary.id))

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).isEmpty()
        }

        @Test
        fun `ticking back in somebody who is not in the selection is refused`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val member = member(incasso = false)

            send(board, sendBody(period.id, listOf(member.id), forciblyIncluded = listOf(GONE)))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.errors[0].field").value("forciblyIncludedUserIds"))
                .andExpect(jsonPath("$.errors[0].code").value("UnknownForcedUserIds"))
                .andExpect(jsonPath("$.errors[0].values[0]").value(GONE))

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).isEmpty()
        }

        @Test
        fun `a payment request is refused without the date it promises`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val member = member(incasso = false)

            send(board, sendBody(period.id, listOf(member.id), paymentDueDate = null))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[0].field").value("paymentDueDate"))
                .andExpect(jsonPath("$.errors[0].code").value("DateRequired"))

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).isEmpty()
        }

        @Test
        fun `a date that has already passed is refused against its own field`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val member = member(incasso = false)

            send(board, sendBody(period.id, listOf(member.id), paymentDueDate = LocalDate.now().minusDays(1)))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[0].field").value("paymentDueDate"))
                .andExpect(jsonPath("$.errors[0].code").value("Future"))

            send(board, sendBody(period.id, listOf(member.id), debitDate = LocalDate.now()))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[0].field").value("debitDate"))
                .andExpect(jsonPath("$.errors[0].code").value("Future"))

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).isEmpty()
        }

        @Test
        fun `a date before the period starts is refused against its own field`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val start = LocalDate.now().plusMonths(2)
            val period = contributionFactory.createPeriod(
                startDate = start,
                endDate = start.plusYears(1),
                halfYearCutoffDate = start.plusMonths(6),
            )
            val member = member(incasso = false, startDate = start)

            send(
                board,
                sendBody(
                    period.id,
                    listOf(member.id),
                    paymentDueDate = LocalDate.now().plusDays(1),
                    debitDate = null,
                ),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[0].field").value("paymentDueDate"))
                .andExpect(jsonPath("$.errors[0].code").value("DateOutsideContributionPeriod"))

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).isEmpty()
        }

        @Test
        fun `a date far beyond the period is refused, one shortly after it is not`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val member = member(incasso = false)

            send(
                board,
                sendBody(period.id, listOf(member.id), paymentDueDate = periodEnd.plusMonths(3).plusDays(1)),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[0].field").value("paymentDueDate"))
                .andExpect(jsonPath("$.errors[0].code").value("DateOutsideContributionPeriod"))

            send(board, sendBody(period.id, listOf(member.id), paymentDueDate = periodEnd.plusMonths(3)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.remindersSent").value(1))
        }

        @Test
        fun `a selection larger than the cap is refused against the field that carries it`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val member = member(incasso = false)
            val overCap: List<Long?> = (1L..1001L).toList()

            send(board, sendBody(period.id, listOf(member.id), forciblyIncluded = overCap))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[0].field").value("forciblyIncludedUserIds"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"))

            send(
                board,
                sendBody(
                    period.id,
                    listOf(member.id),
                    kindOverrides = overCap.associateWith { ContributionEmailKind.REMINDER },
                ),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[0].field").value("kindOverrides"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"))

            send(
                board,
                sendBody(
                    period.id,
                    listOf(member.id),
                    feeTypeOverrides = overCap.associateWith { BulkFeeType.FULL_YEAR_FEE },
                ),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[0].field").value("feeTypeOverrides"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"))
        }

        @Test
        fun `a date nobody in the batch needs may be left out`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val member = member(incasso = false)

            send(board, sendBody(period.id, listOf(member.id), debitDate = null))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.remindersSent").value(1))
        }

        @Test
        fun `an empty selection is refused`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()

            send(board, sendBody(period.id, emptyList())).andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class ReadingOneEmail {

        @Test
        fun `a payment request quotes the amount and where to send it`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val member = member(incasso = false)

            readEmail(board, ContributionEmailKind.REMINDER, period.id, member.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.kind").value("REMINDER"))
                .andExpect(jsonPath("$.feeType").value("FULL_YEAR_FEE"))
                .andExpect(jsonPath("$.recipientEmail").value(member.email))
        }

        @Test
        fun `the kind asked for is the kind rendered, whatever the member's flag says`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val onDirectDebit = member(incasso = true)

            readEmail(board, ContributionEmailKind.REMINDER, period.id, onDirectDebit.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.kind").value("REMINDER"))
        }

        @Test
        fun `a hard-excluded member has no email to read`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val honorary = member(incasso = false, memberType = MemberType.HONORARY)

            readEmail(board, ContributionEmailKind.REMINDER, period.id, honorary.id)
                .andExpect(status().isNotFound)
        }

        @Test
        fun `reading writes nothing`() {
            val board = userFactory.createUserWithRole(Role.BOARD)
            val period = period()
            val member = member(incasso = false)

            readEmail(board, ContributionEmailKind.REMINDER, period.id, member.id).andExpect(status().isOk)

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).isEmpty()
        }
    }

    @Nested
    inner class Authorisation {

        @Test
        fun `a member may not read the table`() {
            val member = userFactory.createUserWithRole(Role.MEMBER)
            val period = period()

            preview(member, period.id, member.id).andExpect(status().isForbidden)
        }

        @Test
        fun `a member may not send`() {
            val member = userFactory.createUserWithRole(Role.MEMBER)
            val period = period()

            send(member, sendBody(period.id, listOf(member.id))).andExpect(status().isForbidden)
        }

        @Test
        fun `a member may not read somebody's email`() {
            val member = userFactory.createUserWithRole(Role.MEMBER)
            val period = period()

            readEmail(member, ContributionEmailKind.REMINDER, period.id, member.id)
                .andExpect(status().isForbidden)
        }
    }

    /** The table and the send read one plan, so this asserts they name the same members. */
    @Test
    fun `the send writes to exactly the members the table said it would`() {
        val board = userFactory.createUserWithRole(Role.BOARD)
        val period = period()
        val onDirectDebit = member(incasso = true)
        val onTransfer = member(incasso = false)
        val honorary = member(incasso = false, memberType = MemberType.HONORARY)
        val selection = listOf(onDirectDebit.id, onTransfer.id, honorary.id)

        val table = preview(board, period.id, *selection.toTypedArray())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        send(board, sendBody(period.id, selection)).andExpect(status().isOk)

        val includedIds = jsonMapper.readTree(table)["rows"]
            .filter { it["disposition"].asString() == "INCLUDED" }
            .map { it["userId"].asLong() }
        val writtenTo = reminderRepository.findByContributionPeriod_Id(period.id!!).map { it.userId } +
            preNotificationRepository.findByContributionPeriod_Id(period.id!!).map { it.userId }

        assertThat(writtenTo).containsExactlyInAnyOrderElementsOf(includedIds)
    }
}
