package net.blueshell.api.platform.integration.contact

import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Live integration tests for BrevoContactAdapter against the real Brevo API.
 *
 * Uses the "brevo-live" profile, which:
 * - deactivates MockContactAdapter (profile = "test | dev")
 * - activates BrevoContactAdapter  (profile = "!test & !dev")
 *
 * Brevo credentials are resolved by Spring from the environment (BREVO_API_KEY etc.)
 * exactly as in production. Run via Docker where the env file is loaded:
 *
 *   docker compose -f docker-compose.dev.yml run api \
 *     ./gradlew :api:test --tests "*.BrevoContactAdapterLiveIT"
 *
 * Tests are ordered: each builds on the state left by the previous one.
 * Teardown deletes the created contact regardless of individual test outcome.
 */
@Tag("brevo-live")
@SpringBootTest
@ActiveProfiles("brevo-live")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrevoContactAdapterLiveIT {

    @Autowired
    private lateinit var adapter: ContactSyncAdapter

    private val testEmail = "live-test-${System.currentTimeMillis()}@esa-blueshell.nl"
    private val testUserId = 99999L
    private var contactId: String? = null

    @AfterAll
    fun teardown() {
        contactId?.let { runCatching { adapter.deleteContact(it) } }
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
    fun `create contact succeeds - verifies listIds is serialised as array`() {
        contactId = adapter.syncContact(testUserId, contactId, contactData())
        assertThat(contactId).isNotNull().isNotBlank()
    }

    @Test
    @Order(2)
    fun `update contact succeeds for existing contact`() {
        assumeContactExists()
        val updatedId = adapter.syncContact(testUserId, contactId, contactData("Updated"))
        assertThat(updatedId).isEqualTo(contactId)
    }

    @Test
    @Order(3)
    fun `create list and add then remove contact`() {
        assumeContactExists()
        val listId = adapter.createList("live-test-${System.currentTimeMillis()}", "contributionPeriods")
        assertThat(listId).isNotNull().isNotBlank()

        adapter.addToList(listId, contactId!!)
        adapter.removeFromList(listId, contactId!!)
        // Note: Brevo lists are not deleted via this adapter; the test list remains in the account.
    }

    @Test
    @Order(4)
    fun `delete contact succeeds`() {
        assumeContactExists()
        adapter.deleteContact(contactId!!)
        contactId = null // prevent teardown from attempting a second delete
    }

    private fun assumeContactExists() {
        Assumptions.assumeTrue(contactId != null, "Skipped: contact from test @Order(1) is required")
    }
}
