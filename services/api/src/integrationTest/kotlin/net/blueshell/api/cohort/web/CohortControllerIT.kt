package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.sync.persistence.ExternalIdMappingRepository
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
class CohortControllerIT : UserTestSupport() {
    @Autowired
    private lateinit var cohorts: CohortRepository

    @Autowired
    private lateinit var targets: TargetRepository

    @Autowired
    private lateinit var externalIds: ExternalIdMappingRepository

    // The cohort's own read carries the states and the strangers; no separate drift report does.

    @Test
    fun `non-admin is forbidden from reading a cohort`() {
        val member = createUserWithRole(Role.MEMBER)
        val cohort = newCohort()

        mvc
            .perform(
                get("/management/cohorts/{id}", cohort.id)
                    .with(signedIn(member)),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `a cohort with no external target reports neither an external id nor a reconcile`() {
        val admin = createUserWithRole(Role.ADMIN)
        val cohort = newCohort()
        newTarget(cohort)

        mvc
            .perform(
                get("/management/cohorts/{id}", cohort.id)
                    .with(signedIn(admin)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.mappings[0].externalId").doesNotExist())
            .andExpect(jsonPath("$.mappings[0].lastReconciledAt").doesNotExist())
            .andExpect(jsonPath("$.members.length()").value(0))
    }

    @Test
    fun `a target reports the folder it is filed in as a path, outside in`() {
        val admin = createUserWithRole(Role.ADMIN)
        val cohort = newCohort()
        newTarget(cohort, externalId = "list-1", folder = "Committees")

        mvc
            .perform(
                get("/management/cohorts/{id}", cohort.id)
                    .with(signedIn(admin)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.mappings[0].path[0]").value("Brevo"))
            .andExpect(jsonPath("$.mappings[0].path[1]").value("Committees"))
    }

    @Test
    fun `an unfiled target is one step deep, not one with a nameless folder`() {
        val admin = createUserWithRole(Role.ADMIN)
        val cohort = newCohort()
        newTarget(cohort, externalId = "list-2")

        mvc
            .perform(
                get("/management/cohorts/{id}", cohort.id)
                    .with(signedIn(admin)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.mappings[0].path.length()").value(1))
            .andExpect(jsonPath("$.mappings[0].path[0]").value("Brevo"))
    }

    @Test
    fun `admin link of an external id owned by another user returns 409`() {
        val admin = createUserWithRole(Role.ADMIN)
        val owner = createUserWithRole(Role.MEMBER)
        val claimant = createUserWithRole(Role.MEMBER)
        val cohort = newCohort()
        externalIds.saveAndFlush(
            ExternalIdMapping("USER", owner.id!!, TargetSystem.BREVO.name, "ext-conflict"),
        )

        mvc
            .perform(
                post("/management/cohorts/{id}/drift/link-user", cohort.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"userId":${claimant.id},"system":"BREVO","externalUserId":"ext-conflict"}""",
                    ).with(signedIn(admin)),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.existingUserId").value(owner.id!!.toInt()))
            .andExpect(jsonPath("$.system").value(TargetSystem.BREVO.name))
    }

    @Test
    fun `non-admin is forbidden from creating a target`() {
        val member = createUserWithRole(Role.MEMBER)
        val cohort = newCohort()

        mvc
            .perform(
                post("/management/cohorts/{id}/targets/new", cohort.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"system":"BREVO","label":"Members"}""")
                    .with(signedIn(member)),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `admin links the cohort to an existing external target`() {
        val admin = createUserWithRole(Role.ADMIN)
        val cohort = newCohort()

        mvc
            .perform(
                post("/management/cohorts/{id}/targets/existing", cohort.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"system":"BREVO","externalId":"list-123"}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.externalId").value("list-123"))

        val target = targets.findByCohortIdAndSystem(cohort.id!!, TargetSystem.BREVO.name)!!
        assertThat(target.externalId).isEqualTo("list-123")
    }

    @Test
    fun `admin creates a fresh external target and maps it`() {
        val admin = createUserWithRole(Role.ADMIN)
        val cohort = newCohort()

        mvc
            .perform(
                post("/management/cohorts/{id}/targets/new", cohort.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"system":"BREVO","label":"Newsletter","folderHint":"Lists"}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.label").value("Newsletter"))
            .andExpect(jsonPath("$.externalId").isNotEmpty)

        val target = targets.findByCohortIdAndSystem(cohort.id!!, TargetSystem.BREVO.name)!!
        assertThat(target.folder).isEqualTo("Lists")
        assertThat(target.externalId).isNotBlank()
    }

    @Test
    fun `creating a list for a registered cohort with no list fills its target, in its folder`() {
        val admin = createUserWithRole(Role.ADMIN)
        val cohort = newCohort()
        val unlinked = newTarget(cohort, folder = "Newsletter")

        mvc
            .perform(
                post("/management/cohorts/{id}/targets/new", cohort.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"system":"BREVO","label":"Members"}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.externalId").isNotEmpty)

        val target = targets.findByCohortIdAndSystem(cohort.id!!, TargetSystem.BREVO.name)!!
        assertThat(target.id).isEqualTo(unlinked.id)
        assertThat(target.folder).isEqualTo("Newsletter")
        assertThat(target.externalId).isNotBlank()
    }

    @Test
    fun `creating a second target for a system the cohort already maps returns 409`() {
        val admin = createUserWithRole(Role.ADMIN)
        val cohort = newCohort()
        newTarget(cohort, externalId = "list-1")

        mvc
            .perform(
                post("/management/cohorts/{id}/targets/new", cohort.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"system":"BREVO","label":"Members"}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isConflict)
    }

    @Test
    fun `admin switches a cohort to a new external target`() {
        val admin = createUserWithRole(Role.ADMIN)
        val cohort = newCohort()
        val target = newTarget(cohort, externalId = "old-list")

        mvc
            .perform(
                put("/management/cohorts/{id}/targets/{targetId}", cohort.id, target.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"externalId":"new-list","deletePrevious":false,"reconcileNow":false}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.externalId").value("new-list"))

        assertThat(targets.findById(target.id!!).orElseThrow().externalId).isEqualTo("new-list")
    }

    @Test
    fun `switching a target that belongs to another cohort returns 404`() {
        val admin = createUserWithRole(Role.ADMIN)
        val ownerCohort = newCohort()
        val otherCohort = newCohort()
        val target = newTarget(ownerCohort)

        mvc
            .perform(
                put("/management/cohorts/{id}/targets/{targetId}", otherCohort.id, target.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"externalId":"new-list","deletePrevious":false,"reconcileNow":false}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isNotFound)
    }

    private fun newCohort(): Cohort = cohorts.save(Cohort(type = CohortType.NEWSLETTER_SUBSCRIBERS, label = "Members"))

    private fun newTarget(
        cohort: Cohort,
        externalId: String? = null,
        folder: String? = null,
    ): Target =
        targets.save(
            Target(
                system = TargetSystem.BREVO.name,
                kind = TargetKind.LIST,
                label = "Members",
                cohortId = cohort.id,
                externalId = externalId,
                folder = folder,
            ),
        )
}
