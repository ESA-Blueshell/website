package net.blueshell.api.platform.integration.queue

import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * A job type with no handler is only discovered at dispatch, where JobExecutor marks
 * the execution dead. Tests that call EmailSenderService directly cannot catch that,
 * so the registry is asserted against the declared types instead.
 */
class EmailJobHandlerCoverageTest : ServiceTestSupport() {

    @Autowired
    private lateinit var jobHandlerRegistry: JobHandlerRegistry

    @Test
    fun `every declared email job type has a registered handler`() {
        val declared = listOf(
            EmailJobs.Recovery.type,
            EmailJobs.EventSignup.type,
            EmailJobs.ContributionReminder.type,
            EmailJobs.IncassoNotification.type,
        )

        assertThat(jobHandlerRegistry.jobTypes()).containsAll(declared)
    }
}
