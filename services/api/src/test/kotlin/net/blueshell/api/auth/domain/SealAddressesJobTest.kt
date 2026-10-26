package net.blueshell.api.auth.domain

import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.shared.job.JobCount
import net.blueshell.api.shared.job.JobTally
import net.blueshell.api.user.api.SealedAddresses
import net.blueshell.api.user.api.UserJobs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper

class SealAddressesJobTest {
    @Test
    fun `seals every address left, and skips when none is`() {
        val addresses: SealedAddresses = mock()
        whenever(addresses.sealEvery()).thenReturn(3, 0)
        val mapper = JsonMapper.builder().build()
        val job = SealAddressesJob(mapper, addresses)

        val sealed = job.handle(mapper.writeValueAsString(UserJobs.SealAddressesPayload()), 5, forced = false)
        val none = job.handle(mapper.writeValueAsString(UserJobs.SealAddressesPayload()), 6, forced = true)

        assertThat((sealed as JobOutcome.Done).counts).containsExactly(JobCount(JobTally.VALUES_SEALED, 3))
        assertThat(none).isInstanceOf(JobOutcome.Skipped::class.java)

        verify(addresses, times(2)).sealEvery()
    }
}
