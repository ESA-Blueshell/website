package net.blueshell.api.platform.integration.contact

import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.domain.user.application.contact.ListSyncAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Live integration tests for ListmonkContactAdapter against the real Listmonk API.
 *
 * Uses the "listmonk-live" profile, which:
 * - deactivates MockContactAdapter (profile = "test | dev")
 * - activates ListmonkContactAdapter  (profile = "!test")
 *
 * Listmonk credentials are resolved by Spring from the environment (LISTMONK_* vars)
 * exactly as in production. Run via Docker where the env file is loaded:
 *
 *   docker compose -f docker-compose.dev.yml run api \
 *     ./gradlew :api:test --tests "*.ListmonkContactAdapterLiveIT"
 *
 * Tests are ordered: each builds on the state left by the previous one.
 * Teardown deletes the created contact regardless of individual test outcome.
 */
@Tag("listmonk-live")
@SpringBootTest
@ActiveProfiles("listmonk-live")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListmonkContactAdapterLiveIT {

    @Autowired
    @Qualifier("listmonkContactAdapter")
    private lateinit var contactAdapter: ContactSyncAdapter

    @Autowired
    @Qualifier("listmonkListAdapter")
    private lateinit var listAdapter: ListSyncAdapter

    private val testEmail = "live-test-${System.currentTimeMillis()}@esa-blueshell.nl"
    private var contactId: Long? = null

    @AfterAll
    fun teardown() {
        contactId?.let { runCatching { contactAdapter.deleteContact(it) } }
    }

    private fun contactData(firstName: String = "LiveTest") = ContactData(
        email = testEmail,
        firstName = firstName,
        lastName = "Integration",
        phoneNumber = null,
        newsletter = false,
        isMember = false
    )

    @Test
    @Order(1)
    fun `create contact succeeds`() {
        contactId = contactAdapter.createContact(contactData())
        assertThat(contactId).isGreaterThan(0)
    }

    @Test
    @Order(2)
    fun `update contact succeeds for existing contact`() {
        assumeContactExists()
        contactAdapter.updateContact(contactId!!, contactData("Updated"))
    }

    @Test
    @Order(3)
    fun `create list and add then remove contact`() {
        assumeContactExists()
        val listId = listAdapter.createList("live-test-${System.currentTimeMillis()}", "contributionPeriods")
        assertThat(listId).isGreaterThan(0)

        listAdapter.addToList(contactId!!, listId)
        listAdapter.removeFromList(contactId!!, listId)
        // Note: Listmonk lists are not deleted via this adapter; the test list remains in the account.
    }

    @Test
    @Order(4)
    fun `delete contact succeeds`() {
        assumeContactExists()
        contactAdapter.deleteContact(contactId!!)
        contactId = null // prevent teardown from attempting a second delete
    }

    private fun assumeContactExists() {
        Assumptions.assumeTrue(contactId != null, "Skipped: contact from test @Order(1) is required")
    }
}
