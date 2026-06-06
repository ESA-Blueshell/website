package net.blueshell.api.platform.integration.cohort.persistence

import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

/**
 * JPA boot + reload smoke test for [CohortMember].
 *
 * The state machine added `state` / `needsPush` as computed getters with
 * no backing field. They are annotated `@get:Transient`; without that
 * Hibernate would treat them as persistent properties and the metamodel
 * would fail to build (or try to map non-existent columns). Persisting and
 * reloading a row proves the entity still maps cleanly and the transient
 * getters resolve from the reloaded field state.
 *
 * Requires a database; runs in CI. Locally it fails with "Connection
 * refused", which is expected.
 */
class CohortMemberPersistenceTest : ServiceTestSupport() {

    @Autowired
    private lateinit var subjects: CohortSubjectRepository

    @Autowired
    private lateinit var cohorts: CohortRepository

    @Autowired
    private lateinit var members: CohortMemberRepository

    @Autowired
    private lateinit var userFactory: UserFactory

    @Test
    fun `persists and reloads a member with its transient state resolving`() {
        val at = LocalDateTime.parse("2026-06-01T10:00:00")

        val user = userFactory.createUserWithRole(Role.MEMBER)
        val userId = user.id

        val memberId = transactionTemplate.execute {
            val subject = subjects.save(CohortSubject(type = CohortSubjectType.CUSTOM, label = "Members"))
            val cohort = cohorts.save(
                Cohort(
                    system = TargetSystem.BREVO,
                    kind = CohortKind.LIST,
                    label = "Members",
                    subjectId = subject.id,
                ),
            )
            val saved = members.save(
                CohortMember(
                    cohort = cohort,
                    userId = userId,
                    subject = subject,
                    externalUserId = "ext-7",
                    syncedAt = at,
                    verifiedAt = at,
                ),
            )
            entityManager.flush()
            entityManager.clear()
            saved.id!!
        }!!

        val reloaded = transactionTemplate.execute { members.findById(memberId).orElseThrow() }!!

        assertThat(reloaded.externalUserId).isEqualTo("ext-7")
        assertThat(reloaded.syncedAt).isEqualTo(at)
        assertThat(reloaded.verifiedAt).isEqualTo(at)
        assertThat(reloaded.state).isEqualTo(CohortMemberState.VERIFIED)
        assertThat(reloaded.needsPush).isFalse()
    }
}
