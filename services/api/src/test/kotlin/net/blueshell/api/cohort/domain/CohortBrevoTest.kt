package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

class CohortBrevoTest {
    private val cohorts: CohortRepository = mock()
    private val targets: TargetRepository = mock()
    private val targetIds: CohortTargetIds = mock()
    private val targeting: CohortTargeting = mock()
    private val registrar: CohortRegistrar = mock()
    private val brevoStrategy: TargetStrategy = mock()
    private val brevo by lazy {
        whenever(brevoStrategy.system).thenReturn(TargetSystem.BREVO)
        CohortBrevo(cohorts, targets, targetIds, targeting, registrar, TargetStrategies(listOf(brevoStrategy)))
    }
    private val cohort = Entities.cohort(id = 5, type = CohortType.COMMITTEE_MEMBERS, label = "Sitecie")
    private val list = Entities.target(id = 50, system = "BREVO", cohortId = 5, externalId = "7", folder = "Committees")

    private fun given(linked: Boolean) {
        whenever(brevoStrategy.available()).thenReturn(true)
        whenever(cohorts.findByDefinitionKey("COMMITTEE_MEMBERS:7")).thenReturn(cohort)
        whenever(cohorts.findById(5)).thenReturn(Optional.of(cohort))
        whenever(targets.findByCohortIdAndSystem(5, "BREVO")).thenReturn(list)
        whenever(targetIds.find(list)).thenReturn(if (linked) "7" else null)
    }

    @Test
    fun `reads the list the seats are on, by Brevo's name and folder, and the target's where Brevo has lost it`() {
        given(linked = true)
        whenever(brevoStrategy.resolve("7")).thenReturn(ExternalTarget(TargetSystem.BREVO, "7", TargetKind.LIST, "Sitecie 2026", "Old"))

        assertThat(brevo.read("COMMITTEE_MEMBERS:7")).isEqualTo(BrevoPlace(true, "7", "Sitecie 2026", "Old"))

        whenever(brevoStrategy.resolve("7")).thenReturn(null)
        assertThat(brevo.read("COMMITTEE_MEMBERS:7")).isEqualTo(BrevoPlace(true, "7", list.label, "Committees"))
    }

    @Test
    fun `reads a committee without a list, and nothing where Brevo cannot be asked`() {
        given(linked = false)
        assertThat(brevo.read("COMMITTEE_MEMBERS:7")).isEqualTo(BrevoPlace(true, null, null, "Committees"))
        whenever(targets.findByCohortIdAndSystem(5, "BREVO")).thenReturn(null)
        assertThat(brevo.read("COMMITTEE_MEMBERS:7")).isEqualTo(BrevoPlace(true, null, null, null))

        whenever(brevoStrategy.available()).thenReturn(false)
        assertThat(brevo.read("COMMITTEE_MEMBERS:7")).isEqualTo(BrevoPlace(false, null, null, null))
        assertThatThrownBy { brevo.apply("COMMITTEE_MEMBERS:7", BrevoChoice(createList = true), "Committees") }
            .isInstanceOf(TargetSystemUnavailable::class.java)
    }

    @Test
    fun `links an existing list or makes one in the folder named, and keeps a list already linked`() {
        given(linked = false)
        brevo.apply("COMMITTEE_MEMBERS:7", BrevoChoice(listId = "9"), "Committees")
        verify(targeting).linkExisting(5, TargetSystem.BREVO, "9")

        brevo.apply("COMMITTEE_MEMBERS:7", BrevoChoice(createList = true), "Committees")
        verify(targeting).create(5, TargetSystem.BREVO, "Sitecie", "Committees")

        brevo.apply("COMMITTEE_MEMBERS:7", BrevoChoice(), "Committees")

        given(linked = true)
        brevo.apply("COMMITTEE_MEMBERS:7", BrevoChoice(listId = "8", createList = true), "Committees")
        verify(targeting, never()).linkExisting(any(), any(), org.mockito.kotlin.eq("8"))
    }

    @Test
    fun `registers a cohort made a moment ago, and refuses a key nothing defines`() {
        whenever(brevoStrategy.available()).thenReturn(true)
        whenever(cohorts.findByDefinitionKey("COMMITTEE_MEMBERS:8")).thenReturn(null, cohort)
        assertThat(brevo.read("COMMITTEE_MEMBERS:8").available).isTrue()
        verify(registrar).register()

        whenever(cohorts.findByDefinitionKey("COMMITTEE_MEMBERS:9")).thenReturn(null)
        assertThatThrownBy { brevo.read("COMMITTEE_MEMBERS:9") }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `knows no Brevo without its strategy`() {
        val none = CohortBrevo(cohorts, targets, targetIds, targeting, registrar, TargetStrategies(emptyList()))
        assertThat(none.read("COMMITTEE_MEMBERS:7").available).isFalse()
    }
}
