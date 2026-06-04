package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.blueshell.api.domain.committee.application.CommitteeService
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CommitteeCohortResolverTest {

    private val provisioning: CohortProvisioningService = mockk(relaxed = true)
    private val committees: CommitteeService = mockk()
    private val resolver = CommitteeCohortResolver(provisioning, committees)

    @Test
    fun `materialize builds a COMMITTEE spec from the committee and delegates to provisioning`() {
        val committee = mockk<Committee> { every { name } returns "Web Committee" }
        every { committees.findById(7L) } returns committee
        val spec = slot<CohortProvisioningSpec>()
        every { provisioning.provision(capture(spec)) } returns CohortProvisioningResult.Ready(mockk())

        resolver.materialize(7L)

        assertThat(spec.captured.factKind).isEqualTo(CohortFactKind.COMMITTEE)
        assertThat(spec.captured.factKey).isEqualTo("7")
        assertThat(spec.captured.subjectType).isEqualTo(CohortSubjectType.COMMITTEE_MEMBERS)
        assertThat(spec.captured.label).isEqualTo("Web Committee")
        assertThat(spec.captured.folder).isEqualTo("Committees")
    }
}
