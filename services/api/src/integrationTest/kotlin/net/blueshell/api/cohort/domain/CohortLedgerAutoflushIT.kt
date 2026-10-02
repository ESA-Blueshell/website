package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetMember
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.cohort.persistence.state
import net.blueshell.api.contact.api.ContactData
import net.blueshell.api.contact.domain.MockContactAdapter
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TargetMemberState
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.sync.persistence.ExternalIdMappingRepository
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class CohortLedgerAutoflushIT : UserTestSupport() {
    @Autowired
    private lateinit var targets: TargetRepository

    @Autowired
    private lateinit var cohorts: CohortRepository

    @Autowired
    private lateinit var members: TargetMemberRepository

    @Autowired
    private lateinit var externalIds: ExternalIdMappingRepository

    @Autowired
    private lateinit var remediation: CohortRemediationService

    @Autowired
    private lateinit var brevo: MockContactAdapter

    @BeforeEach
    fun resetTarget() {
        brevo.clear()
    }

    @Test
    fun `confirming desired row with matching stranger does not violate external unique key`() {
        val user = createUserWithRole(Role.MEMBER)
        val cohort = newCohort()
        val list = brevo.createList("Members", null)
        val remote = brevo.createContact(ContactData("ada@remote.example", "Ada", "Remote", null, false, false))
        brevo.addToList(remote, list)
        val externalUserId = remote.toString()
        val target = newTarget(cohort, externalId = list.toString())
        members.saveAndFlush(TargetMember(target = target, userId = user.id!!, cohort = cohort))
        members.saveAndFlush(
            TargetMember(
                target = target,
                userId = null,
                cohort = cohort,
                externalUserId = externalUserId,
                verifiedAt = LocalDateTime.parse("2026-01-01T12:00:00"),
                label = "old stranger",
            ),
        )
        externalIds.saveAndFlush(ExternalIdMapping("USER", user.id!!, TargetSystem.BREVO.name, externalUserId))

        assertThatCode { remediation.verifyTarget(target.id!!, null) }.doesNotThrowAnyException()

        val desired = members.findByTargetIdAndUserId(target.id!!, user.id!!)!!
        assertThat(desired.externalUserId).isEqualTo(externalUserId)
        assertThat(desired.label).isEqualTo("ada@remote.example")
        assertThat(desired.state).isEqualTo(TargetMemberState.VERIFIED)
        assertThat(members.findByTargetIdAndExternalUserIdAndUserIdIsNull(target.id!!, externalUserId)).isNull()
    }

    @Test
    fun `rapid same-key delete re-add delete uses distinct soft-delete timestamps`() {
        val cohort = newCohort()
        val target = newTarget(cohort, externalId = "list-fast")
        val first =
            members.saveAndFlush(
                TargetMember(
                    target = target,
                    userId = null,
                    cohort = cohort,
                    externalUserId = "ext-fast",
                    verifiedAt = LocalDateTime.parse("2026-01-01T12:00:00"),
                ),
            )

        assertThatCode {
            members.delete(first)
            members.flush()
            val second =
                members.saveAndFlush(
                    TargetMember(
                        target = target,
                        userId = null,
                        cohort = cohort,
                        externalUserId = "ext-fast",
                        verifiedAt = LocalDateTime.parse("2026-01-01T12:00:01"),
                    ),
                )
            members.delete(second)
            members.flush()
        }.doesNotThrowAnyException()

        val deletedAtValues =
            entityManager
                .createNativeQuery(
                    """
                    SELECT DATE_FORMAT(deleted_at, '%Y-%m-%d %H:%i:%s.%f')
                    FROM target_member
                    WHERE target_id = :cohortId
                      AND external_user_id = :externalUserId
                      AND deleted_at <> '9999-12-31 23:59:59'
                    ORDER BY id
                    """.trimIndent(),
                ).setParameter("cohortId", target.id!!)
                .setParameter("externalUserId", "ext-fast")
                .resultList
                .map { it.toString() }

        assertThat(deletedAtValues)
            .hasSize(2)
            .allMatch { it.matches(Regex(""".*\.\d{6}$""")) }
        assertThat(deletedAtValues.toSet()).hasSize(2)
    }

    private fun newCohort(): Cohort = cohorts.saveAndFlush(Cohort(type = CohortType.NEWSLETTER_SUBSCRIBERS, label = "Members"))

    private fun newTarget(
        cohort: Cohort,
        externalId: String,
    ): Target =
        targets.saveAndFlush(
            Target(
                system = TargetSystem.BREVO.name,
                kind = TargetKind.LIST,
                label = "Members",
                cohortId = cohort.id,
                externalId = externalId,
            ),
        )
}
