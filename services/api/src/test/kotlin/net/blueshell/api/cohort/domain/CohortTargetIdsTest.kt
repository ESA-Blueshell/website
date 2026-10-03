package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.job.NonRetryableJobException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException

class CohortTargetIdsTest {
    private val targets: TargetRepository = mockk(relaxed = true)
    private val targetExternalIds = CohortTargetIds(targets)

    private fun target(
        id: Long,
        externalId: String? = null,
    ) = Target(system = "BREVO", kind = TargetKind.LIST, label = "Members").apply {
        this.id = id
        this.externalId = externalId
    }

    @Test
    fun `find reads the column`() {
        assertThat(targetExternalIds.find(target(5L, externalId = "col-1"))).isEqualTo("col-1")
    }

    @Test
    fun `find returns null when the column is unset`() {
        assertThat(targetExternalIds.find(target(5L, externalId = null))).isNull()
        assertThat(targetExternalIds.find(target(5L, externalId = "  "))).isNull()
    }

    @Test
    fun `require throws a terminal failure when the cohort is not materialised`() {
        assertThatThrownBy { targetExternalIds.require(target(5L, externalId = null)) }
            .isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `record writes the column`() {
        val target = target(5L, externalId = null)
        every { targets.findFirstBySystemAndExternalId("BREVO", "list-9") } returns null
        every { targets.save(target) } returns target

        targetExternalIds.record(target, "list-9")

        assertThat(target.externalId).isEqualTo("list-9")
        verify { targets.save(target) }
    }

    @Test
    fun `record rejects a blank external id`() {
        assertThatThrownBy { targetExternalIds.record(target(5L), "  ") }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(exactly = 0) { targets.save(any()) }
    }

    @Test
    fun `record refuses to relink an id already owned by another active cohort`() {
        val target = target(5L)
        every { targets.findFirstBySystemAndExternalId("BREVO", "list-9") } returns target(99L, "list-9")

        assertThatThrownBy { targetExternalIds.record(target, "list-9") }
            .isInstanceOf(ResponseStatusException::class.java)
        verify(exactly = 0) { targets.save(any()) }
    }
}
