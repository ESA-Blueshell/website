package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.api.ContributionService
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.contribution.persistence.IncassoRun
import net.blueshell.api.contribution.persistence.IncassoRunRepository
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.UserErasureService
import net.blueshell.api.user.persistence.IncassoMandate
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional

class IncassoRunsTest {
    private val periods: ContributionPeriodService = mock()
    private val contributions: ContributionService = mock()
    private val memberships: MembershipService = mock()
    private val erasure: UserErasureService = mock()
    private val notifications: IncassoNotificationService = mock()
    private val notificationRows: IncassoNotificationRepository = mock()
    private val runs: IncassoRunRepository = mock()
    private val clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC)
    private val incasso = IncassoRuns(periods, contributions, memberships, erasure, notifications, notificationRows, runs, clock)

    private val period = Entities.period(4, startDate = LocalDate.of(2026, 9, 1)).apply { fullYearFee = 25.0 }

    private val mila = Entities.user(id = 1, username = "mila", firstName = "Mila", lastName = "Vries")
    private val zoe = Entities.user(id = 2, username = "zoe", firstName = "Zoë", lastName = "Bakker")
    private val lotte = Entities.user(id = 3, username = "lotte", firstName = "Lotte", lastName = "Meijer")
    private val bram = Entities.user(id = 4, username = "bram", firstName = "Bram", lastName = "Kok")
    private val ann = Entities.user(id = 5, username = "ann", firstName = "Ann", lastName = "Vos")
    private val hon = Entities.user(id = 6, username = "hon", firstName = "Hon", lastName = "Ored")

    private fun mandate(masked: String) =
        IncassoMandate("sealed", "sealed", masked, "BLUESHELL-$masked", LocalDate.of(2025, 9, 3), null, Instant.EPOCH)

    private fun held(
        user: User,
        lastFour: String?,
    ): Membership =
        Entities.membership(id = 500L + requireNotNull(user.id), user = user, startDate = LocalDate.of(2025, 9, 1)).apply {
            incasso = true
            mandate = lastFour?.let { mandate(it) }
        }

    @BeforeEach
    fun given() {
        whenever(periods.findById(4)).thenReturn(period)
        whenever(memberships.findOverlappingWithMembers(period.startDate, period.endDate)).thenReturn(
            listOf(
                held(mila, "NL34"),
                held(zoe, "DE18"),
                held(lotte, null),
                held(bram, "5560"),
                Entities.membership(user = ann, startDate = LocalDate.of(2025, 9, 1)),
                held(hon, "9999").apply { memberType = MemberType.HONORARY },
            ),
        )
        val paid = Entities.contribution(Contribution.Id(4, 4), bram, period)
        whenever(contributions.findByContributionPeriodId(4)).thenReturn(mutableListOf(paid))
        whenever(notifications.findByContributionPeriodId(4)).thenReturn(mutableListOf())
        whenever(erasure.deletedIdsAmong(any())).thenReturn(emptySet())
        whenever(runs.save(any<IncassoRun>())).thenAnswer { invocation ->
            (invocation.arguments[0] as IncassoRun).also { it.id = 11 }
        }
    }

    @Test
    fun `lists everybody on incasso, masked, with why anybody is left out`() {
        val plan = incasso.plan(4).associateBy { it.name }

        assertThat(plan.keys).containsExactly("Bram Kok", "Hon Ored", "Lotte Meijer", "Mila Vries", "Zoë Bakker")
        assertThat(plan.getValue("Mila Vries").leftOut).isNull()
        assertThat(plan.getValue("Mila Vries").let { listOf(it.ibanCountry, it.ibanLastTwo) }).containsExactly("NL", "34")
        assertThat(plan.getValue("Mila Vries").mandateReference).isEqualTo("BLUESHELL-NL34")
        assertThat(plan.getValue("Mila Vries").amount).isEqualTo(25.0)
        assertThat(plan.getValue("Zoë Bakker").ingName).isEqualTo("Zoe Bakker")
        assertThat(plan.getValue("Lotte Meijer").leftOut).isEqualTo(IncassoLeftOut.NO_BANK_DETAILS)
        assertThat(plan.getValue("Lotte Meijer").membershipId).isEqualTo(500L + requireNotNull(lotte.id))
        assertThat(plan.getValue("Bram Kok").leftOut).isEqualTo(IncassoLeftOut.ALREADY_PAID)
        assertThat(plan.getValue("Hon Ored").leftOut).isEqualTo(IncassoLeftOut.OWES_NOTHING)
    }

    @Test
    fun `a member whose bank details were wiped has none to collect from, and one off incasso is not collected from whatever is on file`() {
        val wiped = held(mila, "NL34").apply { mandate!!.sealedIban = null }
        val stopped = held(zoe, "DE18").apply { incasso = false }
        whenever(memberships.findOverlappingWithMembers(period.startDate, period.endDate)).thenReturn(listOf(wiped, stopped))

        val plan = incasso.plan(4).associateBy { it.name }

        assertThat(plan.keys).containsExactly("Mila Vries")
        assertThat(plan.getValue("Mila Vries").leftOut).isEqualTo(IncassoLeftOut.NO_BANK_DETAILS)
    }

    @Test
    fun `records the run and tells each member the date, amount and account it is collected from`() {
        val recorded = mutableListOf<IncassoNotification>()
        whenever(notifications.record(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as IncassoNotification).also(recorded::add)
        }
        whenever(notificationRows.findByIncassoRunIdIn(listOf(11L))).thenAnswer { recorded.toList() }
        val sent = argumentCaptor<IncassoNotification>()

        val run =
            incasso.start(
                periodId = 4,
                userIds = listOf(1, 2),
                feeTypeOverrides = mapOf(2L to BulkFeeType.HALF_YEAR_FEE),
                collectionDate = LocalDate.of(2026, 11, 1),
                statementText = "Contributie 2026-2027 ESA Blüeshell",
                by = 9,
            )

        verify(notifications, times(2)).record(sent.capture())
        val zoes = sent.allValues.single { it.userId == 2L }
        assertThat(zoes.incassoRunId).isEqualTo(11)
        assertThat(zoes.ibanMasked).isEqualTo("DE18")
        assertThat(zoes.mandateReference).isEqualTo("BLUESHELL-DE18")
        assertThat(zoes.mandateSignedOn).isEqualTo(LocalDate.of(2025, 9, 3))
        assertThat(zoes.feeType).isEqualTo(BulkFeeType.HALF_YEAR_FEE)
        assertThat(zoes.debitDate).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(run.statementText).isEqualTo("Contributie 2026-2027 ESA Blueshell")
        verify(runs).save(argThat<IncassoRun> { createdBy == 9L })
        assertThat(run.collections.map { it.ingName }).containsExactly("Mila Vries", "Zoe Bakker")
        assertThat(run.collections.map { "${it.ibanCountry}${it.ibanLastTwo}" }).containsExactly("NL34", "DE18")
        assertThat(run.total).isEqualTo(sent.allValues.sumOf { it.amount })
        assertThat(run.submittedAt).isNull()
    }

    @Test
    fun `refuses anybody who cannot be collected from, a date not ahead or outside the period, and a missing or long text`() {
        val date = LocalDate.of(2026, 11, 1)
        assertThatThrownBy { incasso.start(4, emptyList(), emptyMap(), date, "x", 9) }
            .isInstanceOf(NothingToCollect::class.java)
        assertThatThrownBy { incasso.start(4, listOf(1, 3, 4, 77), emptyMap(), date, "x", 9) }
            .isInstanceOf(NotCollectable::class.java)
            .extracting("facts")
            .isEqualTo(mapOf("userIds" to listOf(3L, 4L, 77L)))
        assertThatThrownBy { incasso.start(4, listOf(1), emptyMap(), LocalDate.of(2026, 10, 1), "x", 9) }
            .isInstanceOf(CollectionDateNotAhead::class.java)
        assertThatThrownBy { incasso.start(4, listOf(1), emptyMap(), LocalDate.of(2028, 1, 1), "x", 9) }
            .isInstanceOf(CollectionDateOutsidePeriod::class.java)
        assertThatThrownBy { incasso.start(4, listOf(1), emptyMap(), date, " €! ", 9) }
            .isInstanceOf(StatementTextMissing::class.java)
        assertThatThrownBy { incasso.start(4, listOf(1), emptyMap(), date, "a".repeat(141), 9) }
            .isInstanceOf(StatementTextTooLong::class.java)
    }

    @Test
    fun `sums each run of the period, and names a run it does not have`() {
        val run = IncassoRun(4, LocalDate.of(2026, 11, 1), "Contributie", 9, Instant.EPOCH).also { it.id = 11 }
        whenever(runs.findByContributionPeriodIdOrderByCreatedAtDesc(4)).thenReturn(listOf(run))
        whenever(notificationRows.findByIncassoRunIdIn(listOf(11L))).thenReturn(
            listOf(
                IncassoNotification(mila, period, BulkFeeType.FULL_YEAR_FEE, 25.0, run.collectionDate, incassoRunId = 11),
                IncassoNotification(zoe, period, BulkFeeType.HALF_YEAR_FEE, 12.5, run.collectionDate, incassoRunId = 11),
            ),
        )

        val summary = incasso.summariesOf(4).single()
        assertThat(summary.collections).isEqualTo(2)
        assertThat(summary.total).isEqualTo(37.5)
        assertThat(summary.submittedAt).isNull()

        whenever(runs.findById(12)).thenReturn(Optional.empty())
        assertThatThrownBy { incasso.find(12) }.isInstanceOf(IncassoRunNotFound::class.java)
        whenever(runs.findById(11)).thenReturn(Optional.of(run))
        assertThat(incasso.find(11).collections).hasSize(2)
    }

    @Test
    fun `marks a run submitted once, by who said so`() {
        val run = IncassoRun(4, LocalDate.of(2026, 11, 1), "Contributie", 9, Instant.EPOCH).also { it.id = 11 }
        whenever(runs.findById(11)).thenReturn(Optional.of(run))
        whenever(notificationRows.findByIncassoRunIdIn(listOf(11L))).thenReturn(emptyList())

        assertThat(incasso.markSubmitted(11, 3).submittedAt).isEqualTo(clock.instant())
        assertThat(run.submittedBy).isEqualTo(3)
        incasso.markSubmitted(11, 5)
        assertThat(run.submittedBy).isEqualTo(3)
        verify(runs, times(1)).save(run)

        whenever(runs.findById(12)).thenReturn(Optional.empty())
        assertThatThrownBy { incasso.markSubmitted(12, 3) }.isInstanceOf(IncassoRunNotFound::class.java)
    }
}
