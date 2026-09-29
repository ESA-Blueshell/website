package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Optional

class CohortJobSubjectResolverTest {
    private val targets: TargetRepository = mockk()
    private val resolver = CohortJobSubjectResolver(targets)

    @Test
    fun `a job's target reads as its name and where it lives, or by its id once gone`() {
        every { targets.findById(4L) } returns Optional.of(Entities.target(id = 4L, label = "Sitecie"))
        every { targets.findById(5L) } returns Optional.empty()

        assertThat(resolver.label(4L)).isEqualTo("Sitecie (BREVO LIST)")
        assertThat(resolver.label(5L)).isEqualTo("Target #5")
        assertThat(resolver.payloadFields).containsExactly("cohortId")
    }
}
