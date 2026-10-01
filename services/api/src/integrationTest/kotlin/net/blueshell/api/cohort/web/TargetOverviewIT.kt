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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/** The Brevo page reads every list with the cohort it follows, and the missing lists, which the board creates. */
@SpringBootTest
class TargetOverviewIT : UserTestSupport() {
    @Autowired
    private lateinit var targets: TargetRepository

    @Autowired
    private lateinit var cohorts: CohortRepository

    @Autowired
    private lateinit var contactListAdapters: List<ContactListAdapter>

    private val contactLists get() = contactListAdapters.single { it.system == TargetSystem.BREVO }

    @Test
    fun `lists every list and the missing ones, creates a missing one, and members cannot read it`() {
        val board = createUserWithRole(Role.BOARD)
        val stamp = UUID.randomUUID().toString().take(8)
        val listId = contactLists.createList("Followed $stamp", "Members").toString()
        val loose = contactLists.createList("Loose $stamp", null).toString()
        val followed = cohorts.save(Cohort(type = CohortType.PERIOD_MEMBERS, label = "Members $stamp"))
        targets.save(Target(TargetSystem.BREVO.name, TargetKind.LIST, "Followed $stamp", cohortId = followed.id, externalId = listId))
        val unmade = cohorts.save(Cohort(type = CohortType.PERIOD_PAYERS, label = "Paid $stamp"))
        val target =
            targets.save(
                Target(TargetSystem.BREVO.name, TargetKind.LIST, "Paid $stamp", "Contribution paid", cohortId = unmade.id),
            )

        mvc
            .perform(get("/management/cohort-targets/{system}/overview", "BREVO").with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lists[?(@.externalId == '$listId')].cohortLabel").value(hasItem("Members $stamp")))
            .andExpect(jsonPath("$.lists[?(@.externalId == '$loose')].label").value(hasItem("Loose $stamp")))
            .andExpect(jsonPath("$.missing[?(@.targetId == ${target.id})].cohortLabel").value(hasItem("Paid $stamp")))

        mvc
            .perform(
                post("/management/cohort-targets/{system}/missing", "BREVO")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"targetIds":[${target.id}]}""")
                    .with(signedIn(board)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.queued").value(1))

        val member = createUserWithRole(Role.MEMBER)
        mvc.perform(get("/management/cohort-targets/{system}/overview", "BREVO").with(signedIn(member))).andExpect(status().isForbidden)
    }
}
