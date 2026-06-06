package net.blueshell.api.platform.integration.cohort.port.out

import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.NonRetryableJobException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Unit test for [CohortPortRegistry]. Uses a tiny fake [CohortPort] so the
 * registry's lookup contract (require vs find) is exercised without Spring.
 */
class CohortPortRegistryTest {

    private val brevoPort = FakeCohortPort(TargetSystem.BREVO, CohortKind.LIST)
    private val registry = CohortPortRegistry(listOf(brevoPort))

    @Test
    fun `require returns the registered port`() {
        assertThat(registry.require(TargetSystem.BREVO)).isSameAs(brevoPort)
    }

    @Test
    fun `require throws NonRetryableJobException for an unregistered system`() {
        assertThatThrownBy { registry.require(TargetSystem.GOOGLE_CALENDAR) }
            .isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `find returns the registered port`() {
        assertThat(registry.find(TargetSystem.BREVO)).isSameAs(brevoPort)
    }

    @Test
    fun `find returns null for an unregistered system`() {
        assertThat(registry.find(TargetSystem.GOOGLE_CALENDAR)).isNull()
    }

    @Test
    fun `systems reports the registered systems`() {
        assertThat(registry.systems()).containsExactly(TargetSystem.BREVO)
    }

    @Test
    fun `the registered port exposes its kind`() {
        assertThat(registry.require(TargetSystem.BREVO).kind).isEqualTo(CohortKind.LIST)
    }

    private class FakeCohortPort(
        override val system: TargetSystem,
        override val kind: CohortKind,
    ) : CohortPort {
        override fun createCohort(label: String, hint: String?): String = error("not used")
        override fun addMember(externalUserId: String, externalCohortId: String) = Unit
        override fun removeMember(externalUserId: String, externalCohortId: String) = Unit
        override fun deleteCohort(externalCohortId: String) = Unit
        override fun listMembers(externalCohortId: String): List<MemberRef> = emptyList()
    }
}
