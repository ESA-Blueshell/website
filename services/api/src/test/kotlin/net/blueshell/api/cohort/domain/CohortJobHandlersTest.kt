package net.blueshell.api.cohort.domain

import io.mockk.mockk
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.NonRetryableJobException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.annotation.Bean
import tools.jackson.databind.ObjectMapper

/**
 * A job type whose definition loses its binding stops being handled silently — the queue marks its
 * rows dead. These tests read both sides and compare them.
 */
class CohortJobHandlersTest {

    private val objectMapper = ObjectMapper()
    private val handlers = CohortJobHandlers(
        objectMapper,
        reconciliation = mockk(relaxed = true),
        membership = mockk(relaxed = true),
        targeting = mockk(relaxed = true),
        remediation = mockk(relaxed = true),
        inbound = mockk(relaxed = true),
    )

    private val definitions: List<JobDefinition<*>> = CohortJobs::class.nestedClasses
        .mapNotNull { it.objectInstance as? JobDefinition<*> }

    private val bindings: List<CohortJobBinding<*>> = CohortJobHandlers::class.java.methods
        .filter { it.isAnnotationPresent(Bean::class.java) }
        .map { it.invoke(handlers) as CohortJobBinding<*> }

    @Test
    fun `every cohort job definition is bound to a handler`() {
        assertThat(bindings.map { it.jobType })
            .containsExactlyInAnyOrderElementsOf(definitions.map { it.type })
    }

    @Test
    fun `each binding deserialises the payload its definition declares`() {
        val boundPayloads = bindings.associate { it.jobType to it.payloadType }

        assertThat(boundPayloads).isEqualTo(definitions.associate { it.type to it.payloadType })
    }

    @Test
    fun `delete-external-target rejects an unrecognised system without retrying`() {
        val handler = handlers.deleteExternalTargetHandler()

        assertThrows<NonRetryableJobException> {
            handler.handle("""{"system":"BOGUS_SYSTEM","externalTargetId":"ext-1"}""", executionId = null)
        }
    }
}
