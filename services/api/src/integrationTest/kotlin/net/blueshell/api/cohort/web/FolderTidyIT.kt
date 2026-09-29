package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.contact.api.ContactListAdapter
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.testsupport.UserTestSupport
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** The tidy proposes each linked list into its type's folder; applied once, it proposes nothing more. */
@SpringBootTest
class FolderTidyIT : UserTestSupport() {
    @Autowired
    private lateinit var targets: TargetRepository

    @Autowired
    private lateinit var cohorts: CohortRepository

    @Autowired
    private lateinit var contactListAdapters: List<ContactListAdapter>

    private val contactLists get() = contactListAdapters.single { it.system == TargetSystem.BREVO }

    @Test
    fun `preview, apply the pick, and a second preview no longer proposes it`() {
        val admin = createUserWithRole(Role.ADMIN)
        val listId = contactLists.createList("Tidy me", "Old stuff").toString()
        val loose = contactLists.createList("Leave me", "Old stuff").toString()
        val cohort = cohorts.save(Cohort(type = CohortType.PERIOD_ACTIVE_MEMBERS, label = "Tidy me"))
        targets.save(Target(TargetSystem.BREVO.name, TargetKind.LIST, "Tidy me", cohortId = cohort.id, externalId = listId))

        mvc
            .perform(get("/management/cohort-targets/{system}/tidy", "BREVO").with(signedIn(admin)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.moves[?(@.externalId == '$listId')].to").value(hasItem("Active members")))
            .andExpect(jsonPath("$.moves[*].externalId").value(not(hasItem(loose))))

        mvc
            .perform(
                post("/management/cohort-targets/{system}/tidy", "BREVO")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"externalIds":["$listId"]}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.moved[0].externalId").value(listId))
            .andExpect(jsonPath("$.failed.length()").value(0))

        mvc
            .perform(get("/management/cohort-targets/{system}/tidy", "BREVO").with(signedIn(admin)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.moves[*].externalId").value(not(hasItem(listId))))
    }
}
