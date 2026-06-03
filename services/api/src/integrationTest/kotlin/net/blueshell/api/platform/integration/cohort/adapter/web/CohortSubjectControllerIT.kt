package net.blueshell.api.platform.integration.cohort.adapter.web

import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.platform.integration.sync.persistence.repository.ExternalIdMappingRepository
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class CohortSubjectControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var subjects: CohortSubjectRepository

    @Autowired
    private lateinit var cohorts: CohortRepository

    @Autowired
    private lateinit var externalIds: ExternalIdMappingRepository

    @Test
    fun `non-admin is forbidden from reading drift`() {
        val member = createUserWithRole(Role.MEMBER)
        val subject = newSubject()

        mvc.perform(
            get("/management/cohort-subjects/{id}/drift", subject.id)
                .queryParam("system", TargetSystem.BREVO.name)
                .with(bearer(member)),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `admin lists the registered systems`() {
        val admin = createUserWithRole(Role.ADMIN)

        mvc.perform(get("/management/cohort-subjects/systems").with(bearer(admin)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `admin drift read returns a not-materialised report when the cohort has no external mapping`() {
        val admin = createUserWithRole(Role.ADMIN)
        val subject = newSubject()
        newCohort(subject)

        mvc.perform(
            get("/management/cohort-subjects/{id}/drift", subject.id)
                .queryParam("system", TargetSystem.BREVO.name)
                .with(bearer(admin)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.externalCohortId").doesNotExist())
            .andExpect(jsonPath("$.missing.length()").value(0))
            .andExpect(jsonPath("$.extras.length()").value(0))
            .andExpect(jsonPath("$.lastReconciledAt").doesNotExist())
    }

    @Test
    fun `admin reconcile enqueues a reconcile-list job for the resolved cohort`() {
        val admin = createUserWithRole(Role.ADMIN)
        val subject = newSubject()
        newCohort(subject)

        mvc.perform(
            post("/management/cohort-subjects/{id}/drift/reconcile", subject.id)
                .queryParam("system", TargetSystem.BREVO.name)
                .with(bearer(admin)),
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.jobId").isNumber)
    }

    @Test
    fun `admin link of an external id owned by another user returns 409`() {
        val admin = createUserWithRole(Role.ADMIN)
        val owner = createUserWithRole(Role.MEMBER)
        val claimant = createUserWithRole(Role.MEMBER)
        val subject = newSubject()
        externalIds.saveAndFlush(
            ExternalIdMapping("USER", owner.id!!, TargetSystem.BREVO.name, "ext-conflict"),
        )

        mvc.perform(
            post("/management/cohort-subjects/{id}/drift/link-user", subject.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"userId":${claimant.id},"system":"BREVO","externalUserId":"ext-conflict"}""",
                )
                .with(bearer(admin)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.existingUserId").value(owner.id!!.toInt()))
            .andExpect(jsonPath("$.system").value(TargetSystem.BREVO.name))
    }

    private fun newSubject(): CohortSubject =
        subjects.save(CohortSubject(type = CohortSubjectType.CUSTOM, label = "Members"))

    private fun newCohort(subject: CohortSubject): Cohort =
        cohorts.save(
            Cohort(
                system = TargetSystem.BREVO.name,
                kind = CohortKind.LIST,
                label = "Members",
                subjectId = subject.id,
            ),
        )
}
