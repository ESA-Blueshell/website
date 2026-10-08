package net.blueshell.api.auth.domain

import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.shared.job.ExplainedJobFailure
import net.blueshell.api.user.api.RewrapReport
import net.blueshell.api.user.api.SealedValueRewrap
import net.blueshell.api.user.api.UserJobs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper

class RewrapSealedValuesJobTest {
    private val rewrap: SealedValueRewrap = mock()
    private val mapper = JsonMapper.builder().build()
    private val job = RewrapSealedValuesJob(mapper, rewrap)
    private val payload = mapper.writeValueAsString(UserJobs.RewrapSealedValuesPayload())

    @Test
    fun `is done when it moved values, and skipped when every value was on the newest version`() {
        whenever(rewrap.rewrapEvery()).thenReturn(RewrapReport(3, emptyList()), RewrapReport(0, emptyList()))

        assertThat(job.handle(payload, 5, forced = false)).isInstanceOf(JobOutcome.Done::class.java)
        assertThat(job.handle(payload, 6, forced = false)).isInstanceOf(JobOutcome.Skipped::class.java)
    }

    @Test
    fun `fails naming the values left behind, the first ten of many`() {
        whenever(rewrap.rewrapEvery()).thenReturn(
            RewrapReport(4, listOf("address 3")),
            RewrapReport(0, (1..12).map { "address $it" }),
        )

        assertThatThrownBy { job.handle(payload, 5, forced = false) }
            .isInstanceOf(ExplainedJobFailure::class.java)
            .hasMessage("Sealed values left on an older key version: address 3. The next run tries them again.")
        assertThatThrownBy { job.handle(payload, 6, forced = false) }.hasMessageContaining("address 10 and 2 more.")
    }
}
