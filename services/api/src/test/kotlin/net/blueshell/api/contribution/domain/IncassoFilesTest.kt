package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.contribution.persistence.IncassoRun
import net.blueshell.api.contribution.persistence.IncassoRunRepository
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.CollectionAccount
import net.blueshell.api.user.api.CollectionAccounts
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.persistence.IncassoMandate
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import java.util.zip.ZipInputStream

class IncassoFilesTest {
    private val runs: IncassoRunRepository = mock()
    private val notifications: IncassoNotificationRepository = mock()
    private val memberships: MembershipService = mock()
    private val accounts: CollectionAccounts = mock()
    private val clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC)
    private val bank = BankProperties(incassantId = "NL00 ZZZ0 0000 0000 000")
    private val period = Entities.period(4)

    private fun files(bank: BankProperties = this.bank) = IncassoFiles(runs, notifications, memberships, accounts, bank, clock)

    private fun run(
        date: LocalDate = LocalDate.of(2026, 11, 1),
        submittedAt: Instant? = null,
    ) = IncassoRun(4, date, "Contributie", 9, Instant.EPOCH, submittedAt = submittedAt).also { it.id = 11 }

    private fun mandate(reference: String) =
        IncassoMandate("k1", "sealed", "sealed", "4300", reference, LocalDate.of(2025, 9, 3), null, Instant.EPOCH)

    private fun told(user: User) =
        IncassoNotification(
            user = user,
            contributionPeriod = period,
            feeType = BulkFeeType.FULL_YEAR_FEE,
            amount = 25.0,
            debitDate = LocalDate.of(2026, 11, 1),
            incassoRunId = 11,
            mandateReference = "BLUESHELL-${user.id}",
            mandateSignedOn = LocalDate.of(2025, 9, 3),
            ibanLastFour = "4300",
        )

    private fun given(
        members: List<User>,
        changed: Set<Long> = emptySet(),
    ) {
        whenever(runs.findById(11)).thenReturn(Optional.of(run()))
        whenever(notifications.findByIncassoRunIdIn(listOf(11L))).thenReturn(members.map(::told))
        whenever(memberships.findByUserIdsWithMembers(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[0] as Collection<Long>).associateWith { id ->
                val member = members.first { it.id == id }
                listOf(
                    Entities.membership(user = member).apply {
                        mandate =
                            mandate(
                                if (id in
                                    changed
                                ) {
                                    "BLUESHELL-NEW"
                                } else {
                                    "BLUESHELL-$id"
                                },
                            )
                    },
                )
            }
        }
        whenever(accounts.of(any())).thenReturn(CollectionAccount("NL91ABNA0417164300", "Zoë Bakker"))
    }

    @Test
    fun `fills one file with each member's full account under the reference they were told`() {
        given(listOf(Entities.user(id = 1, firstName = "Zoë", lastName = "Bakker")))

        val file = files().file(11, 1)

        assertThat(file.name).isEqualTo("incassobatch-2026-11-01.xlsx")
        val sheet =
            ZipInputStream(ByteArrayInputStream(file.bytes)).use { zip ->
                generateSequence { zip.nextEntry }.first { it.name == "xl/worksheets/sheet1.xml" }.let { zip.readBytes().decodeToString() }
            }
        assertThat(sheet).contains("<t>Zoe Bakker</t>", "<t>NL91ABNA0417164300</t>", "<t>BLUESHELL-1</t>", "<t>NL00ZZZ000000000000</t>")
    }

    @Test
    fun `splits more than a thousand collections over several files`() {
        given((1L..1001L).map { Entities.user(id = it, username = "u$it") })

        assertThat(files().file(11, 2).name).isEqualTo("incassobatch-2026-11-01-2-of-2.xlsx")
        assertThatThrownBy { files().file(11, 3) }.isInstanceOf(IncassoFilePartNotFound::class.java)
    }

    @Test
    fun `refuses a run in ING, past its date, without the association's details, or whose mandates changed`() {
        whenever(runs.findById(12)).thenReturn(Optional.empty())
        assertThatThrownBy { files().file(12, 1) }.isInstanceOf(IncassoRunNotFound::class.java)

        whenever(runs.findById(11)).thenReturn(Optional.of(run(submittedAt = Instant.EPOCH)))
        assertThatThrownBy { files().file(11, 1) }.isInstanceOf(IncassoRunSubmitted::class.java)
        whenever(runs.findById(11)).thenReturn(Optional.of(run(date = LocalDate.of(2026, 10, 1))))
        assertThatThrownBy { files().file(11, 1) }.isInstanceOf(CollectionDatePassed::class.java)
        whenever(runs.findById(11)).thenReturn(Optional.of(run()))
        assertThatThrownBy { files(BankProperties()).file(11, 1) }.isInstanceOf(IngDetailsMissing::class.java)
        assertThatThrownBy { files(BankProperties(iban = "NL19", incassantId = bank.incassantId)).file(11, 1) }
            .isInstanceOf(IngDetailsMissing::class.java)

        given(listOf(Entities.user(id = 1), Entities.user(id = 2, username = "two")), changed = setOf(2))
        assertThatThrownBy { files().file(11, 1) }
            .isInstanceOf(MandateChanged::class.java)
            .extracting("facts")
            .isEqualTo(mapOf("userIds" to listOf(2L)))
    }
}
