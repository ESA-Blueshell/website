package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetDeletionRepository
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
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
    private lateinit var targets: TargetRepository

    @Autowired
    private lateinit var contactListAdapters: List<ContactListAdapter>

    @Autowired
    private lateinit var deletions: TargetDeletionRepository

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
        val linked = targets.save(Target(TargetSystem.BREVO.name, TargetKind.LIST, "Members", externalId = listId.toString()))

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
        assertThat(targets.findById(linked.id!!).orElseThrow().label).isEqualTo("Members 2026-2027")
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

    @Test
    fun `archiving files a list in the archive folder and keeps its contacts and link`() {
        val admin = createUserWithRole(Role.ADMIN)
        val listId = contactLists.createList("Paid 2024-2025", "Contribution paid")
        val linked = targets.save(Target(TargetSystem.BREVO.name, TargetKind.LIST, "Paid 2024-2025", externalId = listId.toString()))

        mvc
            .perform(post("/management/cohort-targets/{system}/{externalId}/archive", "BREVO", listId).with(signedIn(admin)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.folderLabel").value("Archive"))
            .andExpect(jsonPath("$.linkedCohortId").value(linked.id!!))

        val folders = contactLists.listFolders()
        assertThat(folders[contactLists.listAll().single { it.externalListId == listId }.folderId]).isEqualTo("Archive")
    }

    @Test
    fun `an unlinked list is deleted by its exact name, and the delete is recorded`() {
        val admin = createUserWithRole(Role.ADMIN)
        val listId = contactLists.createList("Old test list", null)

        mvc
            .perform(
                post("/management/cohort-targets/{system}/{externalId}/delete", "BREVO", listId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Old test list"}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isNoContent)

        assertThat(contactLists.listAll().none { it.externalListId == listId }).isTrue()
        val recorded = deletions.findAll().single { it.externalId == listId.toString() }
        assertThat(recorded.name).isEqualTo("Old test list")
        assertThat(recorded.deletedBy).isEqualTo(admin.id)
    }

    @Test
    fun `a linked list is not deleted, nor one whose name was mistyped`() {
        val admin = createUserWithRole(Role.ADMIN)
        val linkedId = contactLists.createList("Members", null)
        targets.save(Target(TargetSystem.BREVO.name, TargetKind.LIST, "Members", externalId = linkedId.toString()))
        val looseId = contactLists.createList("Loose", null)

        mvc
            .perform(
                post("/management/cohort-targets/{system}/{externalId}/delete", "BREVO", linkedId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Members"}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TargetStillLinked"))
        mvc
            .perform(
                post("/management/cohort-targets/{system}/{externalId}/delete", "BREVO", looseId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"loose"}""")
                    .with(signedIn(admin)),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("TargetNameMismatch"))

        assertThat(contactLists.listAll().map { it.externalListId }).contains(linkedId, looseId)
    }
}
