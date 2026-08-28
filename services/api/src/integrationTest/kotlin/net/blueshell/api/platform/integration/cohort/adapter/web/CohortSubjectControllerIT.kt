package net.blueshell.api.platform.integration.cohort.adapter.web

import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.sync.persistence.ExternalIdMappingRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
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

    // The drift report is gone: the subject's own read carries the states and the strangers,
    // so these two cases follow it there rather than being deleted with the endpoint.

    @Test
    fun `non-admin is forbidden from reading a subject`() {
        val member = createUserWithRole(Role.MEMBER)
        val subject = newSubject()

        mvc.perform(
            get("/management/cohort-subjects/{id}", subject.id)
                .with(bearer(member)),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `a cohort with no external target reports neither an external id nor a reconcile`() {
        val admin = createUserWithRole(Role.ADMIN)
        val subject = newSubject()
        newCohort(subject)

        mvc.perform(
            get("/management/cohort-subjects/{id}", subject.id)
                .with(bearer(admin)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mappings[0].externalId").doesNotExist())
            .andExpect(jsonPath("$.mappings[0].lastReconciledAt").doesNotExist())
            .andExpect(jsonPath("$.members.length()").value(0))
    }

    @Test
    fun `a target reports the folder it is filed in as a path, outside in`() {
        val admin = createUserWithRole(Role.ADMIN)
        val subject = newSubject()
        newCohort(subject, externalId = "list-1", folder = "Committees")

        mvc.perform(
            get("/management/cohort-subjects/{id}", subject.id)
                .with(bearer(admin)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mappings[0].path[0]").value("Brevo"))
            .andExpect(jsonPath("$.mappings[0].path[1]").value("Committees"))
    }

    @Test
    fun `an unfiled target is one step deep, not one with a nameless folder`() {
        val admin = createUserWithRole(Role.ADMIN)
        val subject = newSubject()
        newCohort(subject, externalId = "list-2")

        mvc.perform(
            get("/management/cohort-subjects/{id}", subject.id)
                .with(bearer(admin)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mappings[0].path.length()").value(1))
            .andExpect(jsonPath("$.mappings[0].path[0]").value("Brevo"))
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

    @Test
    fun `non-admin is forbidden from creating a target`() {
        val member = createUserWithRole(Role.MEMBER)
        val subject = newSubject()

        mvc.perform(
            post("/management/cohort-subjects/{id}/targets/new", subject.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"system":"BREVO","label":"Members"}""")
                .with(bearer(member)),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `admin links the subject to an existing external target`() {
        val admin = createUserWithRole(Role.ADMIN)
        val subject = newSubject()

        mvc.perform(
            post("/management/cohort-subjects/{id}/targets/existing", subject.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"system":"BREVO","externalId":"list-123"}""")
                .with(bearer(admin)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.externalId").value("list-123"))

        val cohort = cohorts.findBySubjectIdAndSystem(subject.id!!, TargetSystem.BREVO.name)!!
        assertThat(cohort.externalId).isEqualTo("list-123")
    }

    @Test
    fun `admin creates a fresh external target and maps it`() {
        val admin = createUserWithRole(Role.ADMIN)
        val subject = newSubject()

        mvc.perform(
            post("/management/cohort-subjects/{id}/targets/new", subject.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"system":"BREVO","label":"Newsletter","folderHint":"Lists"}""")
                .with(bearer(admin)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.label").value("Newsletter"))
            .andExpect(jsonPath("$.externalId").isNotEmpty)

        val cohort = cohorts.findBySubjectIdAndSystem(subject.id!!, TargetSystem.BREVO.name)!!
        assertThat(cohort.folder).isEqualTo("Lists")
        assertThat(cohort.externalId).isNotBlank()
    }

    @Test
    fun `creating a second target for a system the subject already maps returns 409`() {
        val admin = createUserWithRole(Role.ADMIN)
        val subject = newSubject()
        newCohort(subject)

        mvc.perform(
            post("/management/cohort-subjects/{id}/targets/new", subject.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"system":"BREVO","label":"Members"}""")
                .with(bearer(admin)),
        ).andExpect(status().isConflict)
    }

    @Test
    fun `admin switches a cohort to a new external target`() {
        val admin = createUserWithRole(Role.ADMIN)
        val subject = newSubject()
        val cohort = newCohort(subject, externalId = "old-list")

        mvc.perform(
            put("/management/cohort-subjects/{id}/targets/{cohortId}", subject.id, cohort.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"externalId":"new-list","deletePrevious":false,"reconcileNow":false}""")
                .with(bearer(admin)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.externalId").value("new-list"))

        assertThat(cohorts.findById(cohort.id!!).orElseThrow().externalId).isEqualTo("new-list")
    }

    @Test
    fun `switching a cohort that belongs to another subject returns 404`() {
        val admin = createUserWithRole(Role.ADMIN)
        val ownerSubject = newSubject()
        val otherSubject = newSubject()
        val cohort = newCohort(ownerSubject)

        mvc.perform(
            put("/management/cohort-subjects/{id}/targets/{cohortId}", otherSubject.id, cohort.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"externalId":"new-list","deletePrevious":false,"reconcileNow":false}""")
                .with(bearer(admin)),
        ).andExpect(status().isNotFound)
    }

    private fun newSubject(): CohortSubject =
        subjects.save(CohortSubject(type = CohortSubjectType.NEWSLETTER_SUBSCRIBERS, label = "Members"))

    private fun newCohort(
        subject: CohortSubject,
        externalId: String? = null,
        folder: String? = null,
    ): Cohort =
        cohorts.save(
            Cohort(
                system = TargetSystem.BREVO.name,
                kind = CohortKind.LIST,
                label = "Members",
                subjectId = subject.id,
                externalId = externalId,
                folder = folder,
            ),
        )
}
