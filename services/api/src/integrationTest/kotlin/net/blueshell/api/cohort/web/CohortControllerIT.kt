package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.DriftResolutionAction
import net.blueshell.api.cohort.persistence.DriftResolutionRepository
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetMember
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.contact.api.ContactListAdapter
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

    @Autowired
    private lateinit var members: TargetMemberRepository

    @Autowired
    private lateinit var resolutions: DriftResolutionRepository

    @Autowired
    private lateinit var contactListAdapters: List<ContactListAdapter>

    private val contactLists get() = contactListAdapters.single { it.system == TargetSystem.BREVO }

    // The drift report is gone: the cohort's own read carries the states and the strangers,
    // so these two cases follow it there rather than being deleted with the endpoint.

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
    fun `a target reports the folder Brevo files it in as a path, outside in`() {
        val admin = createUserWithRole(Role.ADMIN)
        val cohort = newCohort()
        // Created for another folder: the page follows Brevo, not the row.
        val listId = contactLists.createList("Sitecie", "Committees")
        newTarget(cohort, externalId = listId.toString(), folder = "Members")

        mvc
            .perform(
                get("/management/cohorts/{id}", cohort.id)
                    .with(signedIn(admin)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.mappings[0].path[0]").value("Brevo"))
            .andExpect(jsonPath("$.mappings[0].path[1]").value("Committees"))
            .andExpect(jsonPath("$.mappings[0].folderKnown").value(true))
    }

    @Test
    fun `a target Brevo cannot place shows its folder as unknown`() {
        val admin = createUserWithRole(Role.ADMIN)
        val cohort = newCohort()
        newTarget(cohort, externalId = "999999", folder = "Committees")

        mvc
            .perform(
                get("/management/cohorts/{id}", cohort.id)
                    .with(signedIn(admin)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.mappings[0].path.length()").value(1))
            .andExpect(jsonPath("$.mappings[0].path[0]").value("Brevo"))
            .andExpect(jsonPath("$.mappings[0].folderKnown").value(false))
    }

    @Test
    fun `linking a contact owned by another account reports the conflict and links the rest`() {
        val admin = createUserWithRole(Role.ADMIN)
        val owner = createUserWithRole(Role.MEMBER)
        val claimant = createUserWithRole(Role.MEMBER)
        val other = createUserWithRole(Role.MEMBER)
        val cohort = newCohort()
        val target = newTarget(cohort, externalId = "list-9")
        members.save(TargetMember(target, null, cohort, externalUserId = "ext-conflict", label = "a@example.com"))
        members.save(TargetMember(target, null, cohort, externalUserId = "ext-free", label = "b@example.com"))
        externalIds.saveAndFlush(ExternalIdMapping("USER", owner.id!!, TargetSystem.BREVO.name, "ext-conflict"))

        mvc
            .perform(
                post("/management/cohorts/{id}/targets/{targetId}/drift/link", cohort.id, target.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"links":[{"externalUserId":"ext-conflict","userId":${claimant.id}},""" +
                            """{"externalUserId":"ext-free","userId":${other.id}}]}""",
                    ).with(signedIn(admin)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.linked").value(1))
            .andExpect(jsonPath("$.conflicts[0].externalUserId").value("ext-conflict"))
            .andExpect(jsonPath("$.conflicts[0].existingUserId").value(owner.id!!.toInt()))

        mvc
            .perform(get("/management/cohorts/{id}", cohort.id).with(signedIn(admin)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resolutions.length()").value(1))
            .andExpect(jsonPath("$.resolutions[0].action").value("LINK"))
            .andExpect(jsonPath("$.resolutions[0].externalUserId").value("ext-free"))
            .andExpect(jsonPath("$.resolutions[0].resolvedByName").value(admin.fullName))
    }

    @Test
    fun `removing theirs-only people records each removal`() {
        val admin = createUserWithRole(Role.ADMIN)
        val cohort = newCohort()
        val target = newTarget(cohort, externalId = "list-10")
        members.save(TargetMember(target, null, cohort, externalUserId = "ext-1", label = "c@example.com"))

        mvc
            .perform(
                post("/management/cohorts/{id}/targets/{targetId}/drift/remove", cohort.id, target.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"externalUserIds":["ext-1","ext-unknown"]}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.resolved").value(1))

        assertThat(resolutions.findTop20ByTargetIdInOrderByResolvedAtDesc(listOf(target.id!!)))
            .singleElement()
            .satisfies({ assertThat(it.action).isEqualTo(DriftResolutionAction.REMOVE) })
    }

    @Test
    fun `resolving drift on a target not yet created is refused`() {
        val admin = createUserWithRole(Role.ADMIN)
        val cohort = newCohort()
        val target = newTarget(cohort)

        mvc
            .perform(
                post("/management/cohorts/{id}/targets/{targetId}/drift/push", cohort.id, target.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userIds":[1]}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TargetNotCreated"))
    }

    @Test
    fun `only an admin enforces a target`() {
        val board = createUserWithRole(Role.BOARD)
        val admin = createUserWithRole(Role.ADMIN)
        val cohort = newCohort()
        val target = newTarget(cohort, externalId = "list-11")

        mvc
            .perform(
                put("/management/cohorts/{id}/targets/{targetId}/enforced", cohort.id, target.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"enforced":true}""")
                    .with(signedIn(board)),
            ).andExpect(status().isForbidden)
        mvc
            .perform(
                put("/management/cohorts/{id}/targets/{targetId}/enforced", cohort.id, target.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"enforced":true}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isNoContent)

        assertThat(targets.findById(target.id!!).orElseThrow().enforced).isTrue()
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
    fun `switching a cohort that belongs to another cohort returns 404`() {
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
