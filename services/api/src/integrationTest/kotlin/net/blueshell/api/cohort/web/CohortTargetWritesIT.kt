package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.contact.api.ContactListAdapter
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** The board makes lists and folders in Brevo, and renames any list, from the lists page. */
@SpringBootTest
class CohortTargetWritesIT : UserTestSupport() {
    @Autowired
    private lateinit var cohorts: CohortRepository

    @Autowired
    private lateinit var contactListAdapters: List<ContactListAdapter>

    private val contactLists get() = contactListAdapters.single { it.system == TargetSystem.BREVO }

    @Test
    fun `a new list is made in the folder picked, and comes back unlinked`() {
        val admin = createUserWithRole(Role.ADMIN)

        mvc
            .perform(
                post("/management/cohort-targets/{system}", "BREVO")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Pub quiz 2026","folder":"Committees"}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.label").value("Pub quiz 2026"))
            .andExpect(jsonPath("$.folderLabel").value("Committees"))
            .andExpect(jsonPath("$.linkedCohortId").doesNotExist())
    }

    @Test
    fun `a new folder is made by name, once`() {
        val admin = createUserWithRole(Role.ADMIN)

        repeat(2) {
            mvc
                .perform(
                    post("/management/cohort-targets/{system}/folders", "BREVO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"Projects"}""")
                        .with(signedIn(admin)),
                ).andExpect(status().isOk)
        }

        assertThat(contactLists.listFolders().values.count { it == "Projects" }).isEqualTo(1)
    }

    @Test
    fun `renaming a linked list renames it in Brevo and on its cohort`() {
        val admin = createUserWithRole(Role.ADMIN)
        val listId = contactLists.createList("Members", "Members")
        val linked = cohorts.save(Cohort(TargetSystem.BREVO.name, CohortKind.LIST, "Members", externalId = listId.toString()))

        mvc
            .perform(
                put("/management/cohort-targets/{system}/{externalId}/name", "BREVO", listId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Members 2026-2027"}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.label").value("Members 2026-2027"))
            .andExpect(jsonPath("$.linkedCohortId").value(linked.id!!))

        assertThat(contactLists.listAll().single { it.externalListId == listId }.name).isEqualTo("Members 2026-2027")
        assertThat(cohorts.findById(linked.id!!).orElseThrow().label).isEqualTo("Members 2026-2027")
    }

    @Test
    fun `renaming a list Brevo does not have is refused as not found`() {
        val admin = createUserWithRole(Role.ADMIN)

        mvc
            .perform(
                put("/management/cohort-targets/{system}/{externalId}/name", "BREVO", "987654")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Gone"}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("TargetNotFound"))
    }

    @Test
    fun `nobody but an admin makes a list`() {
        val board = createUserWithRole(Role.BOARD)

        mvc
            .perform(
                post("/management/cohort-targets/{system}", "BREVO")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Sneaky"}""")
                    .with(signedIn(board)),
            ).andExpect(status().isForbidden)
    }
}
