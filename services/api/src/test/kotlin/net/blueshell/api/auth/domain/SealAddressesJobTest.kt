package net.blueshell.api.auth.domain

import net.blueshell.api.user.api.SealedAddresses
import net.blueshell.api.user.api.UserJobs
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

        job.handle(mapper.writeValueAsString(UserJobs.SealAddressesPayload()), 5, forced = false)
        job.handle(mapper.writeValueAsString(UserJobs.SealAddressesPayload()), 6, forced = true)

        verify(addresses, times(2)).sealEvery()
    }
}
