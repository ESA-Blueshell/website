package net.blueshell.api.declaration.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.declaration.persistence.Declaration
import net.blueshell.api.declaration.persistence.DeclarationExpense
import net.blueshell.api.declaration.persistence.DeclarationRepository
import net.blueshell.api.declaration.persistence.DeclarationState
import net.blueshell.api.declaration.persistence.DeclarationStep
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils.setField
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional

/** A declaration of user 12 in [state], holding [expenses] as description and cents. */
internal fun declarationOf(
    id: Long,
    state: DeclarationState,
    vararg expenses: Pair<String, Int?>,
) = Declaration(12, Instant.EPOCH).apply {
    setField(this, "id", id)
    this.state = state
    confirmed = true
    steps += DeclarationStep(DeclarationState.DRAFT, Instant.EPOCH, 12)
    if (state != DeclarationState.DRAFT) steps += DeclarationStep(DeclarationState.SUBMITTED, Instant.EPOCH, 12)
    replaceExpenses(
        expenses.mapIndexed { index, (words, cents) ->
            DeclarationExpense(this, index, LocalDate.of(2026, 10, 1), words, cents, null).also { setField(it, "id", id * 10 + index) }
        },
    )
}

class DeclarationReviewsTest {
    private val now = Instant.parse("2026-10-20T09:00:00Z")
    private val repository: DeclarationRepository = mockk()
    private val jobs: JobQueue = mockk(relaxed = true)
    private val reviews = DeclarationReviews(repository, jobs, Clock.fixed(now, ZoneOffset.UTC))
    private val submitted = declarationOf(40, DeclarationState.SUBMITTED, " Pizza " to 1250, "Drinks" to null, "Cups" to 250)

    init {
        every { repository.findById(any()) } returns Optional.empty()
        every { repository.findById(40) } returns Optional.of(submitted)
    }

    @Test
    fun `the board sees what is past draft, and a draft, sent back or never sent, is not found`() {
        every { repository.findAllByStateNotOrderByCreatedAtDescIdDesc(DeclarationState.DRAFT) } returns listOf(submitted)
        every { repository.findById(41) } returns Optional.of(declarationOf(41, DeclarationState.DRAFT, "Snacks" to 500))

        assertThat(reviews.all()).containsExactly(submitted)
        assertThat(reviews.one(40)).isSameAs(submitted)
        assertThatThrownBy { reviews.one(41) }.isInstanceOf(DeclarationNotFound::class.java)
        assertThatThrownBy { reviews.one(42) }.isInstanceOf(DeclarationNotFound::class.java)
    }

    @Test
    fun `approving records who and when, and only a submitted declaration is approved`() {
        assertThat(reviews.approve(40, 3).state).isEqualTo(DeclarationState.APPROVED)

        assertThat(submitted.steps.last()).isEqualTo(DeclarationStep(DeclarationState.APPROVED, now, 3))
        assertThatThrownBy { reviews.approve(40, 3) }
            .isInstanceOf(DeclarationStateChanged::class.java)
            .extracting("facts")
            .isEqualTo(mapOf("state" to "APPROVED"))
        verify(exactly = 0) { jobs.runAsync(any<DeclarationJobs.DeclarationReturned>(), any(), any(), any()) }
    }

    @Test
    fun `sending back makes a draft that asks the statement again, and queues the declaration as the board saw it`() {
        val back = reviews.sendBack(40, 3, " The receipt is cut off. ")

        assertThat(back.state).isEqualTo(DeclarationState.DRAFT)
        assertThat(back.confirmed).isFalse()
        assertThat(submitted.steps.last().reason).isEqualTo("The receipt is cut off.")
        val payload =
            DeclarationJobs.DeclarationReturnedPayload(
                40,
                12,
                DeclarationJobs.Outcome.SENT_BACK,
                "The receipt is cut off.",
                "Pizza and 2 more",
                1500,
            )
        verify { jobs.runAsync(DeclarationJobs.DeclarationReturned, payload, JobTrigger.SITE_ACTION, null) }
        // Sent back, it is a draft again and the board no longer sees it.
        assertThatThrownBy { reviews.reject(40, 3, "Late") }.isInstanceOf(DeclarationNotFound::class.java)
    }

    @Test
    fun `rejecting is final, from submitted or approved, and asks a reason`() {
        reviews.approve(40, 3)
        assertThatThrownBy { reviews.reject(40, 3, "  ") }.isInstanceOf(ReasonMissing::class.java)

        assertThat(reviews.reject(40, 3, "Not for the association.").state).isEqualTo(DeclarationState.REJECTED)

        val rejected = DeclarationJobs.Outcome.REJECTED
        verify { jobs.runAsync(DeclarationJobs.DeclarationReturned, match { it.outcome == rejected }, any(), any()) }
        assertThatThrownBy { reviews.sendBack(40, 3, "Again") }.isInstanceOf(DeclarationStateChanged::class.java)
    }

    @Test
    fun `a declaration without expenses is named as a new one`() {
        assertThat(titleOf(declarationOf(43, DeclarationState.SUBMITTED))).isEqualTo("New declaration")
        assertThat(titleOf(declarationOf(44, DeclarationState.SUBMITTED, "Pizza" to 100))).isEqualTo("Pizza")
    }
}
