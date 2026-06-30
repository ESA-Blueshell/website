package net.blueshell.api.platform.integration.cohort.adapter.brevo

import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.port.out.ExternalMember
import net.blueshell.api.platform.integration.cohort.port.out.ExternalTarget
import net.blueshell.api.platform.integration.cohort.port.out.TargetCapability
import net.blueshell.api.platform.integration.cohort.port.out.TargetDescriptor
import net.blueshell.api.platform.integration.cohort.port.out.TargetStrategy
import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.platform.integration.contact.adapter.ContactServiceException
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.clients.brevo.api.ContactsApi
import net.blueshell.clients.brevo.model.GetLists200ResponseListsInner
import net.blueshell.clients.brevo.model.GetProcessesSortParameter
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException

@Service
@Profile("!test & !dev")
class BrevoTargetStrategy(
    contactListAdapters: List<ContactListAdapter>,
    private val contactsApi: ContactsApi,
) : TargetStrategy {
    private val lists = contactListAdapters.single { it.system == ContactSystem.BREVO }

    override val descriptor = TargetDescriptor(
        system = TargetSystem.BREVO,
        kind = CohortKind.LIST,
        systemLabel = "Brevo",
        targetLabel = "Brevo list",
        idLabel = "List id",
        folderLabel = "Folder",
        capabilities = setOf(
            TargetCapability.CATALOG,
            TargetCapability.CREATE,
            TargetCapability.READ_MEMBERS,
            TargetCapability.WRITE_MEMBERS,
            TargetCapability.DELETE,
        ),
    )

    override fun catalog(query: String?): List<ExternalTarget> {
        val folderNames = folders()
        val q = query?.trim()?.lowercase().orEmpty()
        return listTargets(folderNames)
            .filter { it.matches(q) }
            .sortedWith(compareBy({ it.folderLabel.orEmpty() }, { it.label }))
    }

    override fun create(label: String, folder: String?): ExternalTarget =
        ExternalTarget(
            system = system,
            externalId = lists.createList(label, folder).toString(),
            kind = descriptor.kind,
            label = label,
            folderLabel = folder,
        )

    override fun members(target: ExternalTarget): List<ExternalMember> =
        lists.listMembers(target.externalId.toBrevoId("externalId", "members"))
            .map { ExternalMember(it.externalUserId.toString(), it.email) }
            .filter { it.externalUserId.isNotBlank() }

    override fun add(target: ExternalTarget, externalUserId: String) {
        lists.addToList(
            externalUserId.toBrevoId("externalUserId", "add"),
            target.externalId.toBrevoId("externalId", "add"),
        )
    }

    override fun remove(target: ExternalTarget, externalUserId: String) {
        lists.removeFromList(
            externalUserId.toBrevoId("externalUserId", "remove"),
            target.externalId.toBrevoId("externalId", "remove"),
        )
    }

    override fun delete(target: ExternalTarget) {
        lists.deleteList(target.externalId.toBrevoId("externalId", "delete"))
    }

    private fun folders(): Map<String, String> =
        page("folders") { limit, offset ->
            contactsApi.getFolders(limit, offset, GetProcessesSortParameter.ASC).let { page ->
                Page(page.count, page.folders.orEmpty().associate { it.id.toString() to it.name }.entries.toList())
            }
        }.associate { it.key to it.value }

    private fun listTargets(folderNames: Map<String, String>): List<ExternalTarget> =
        page("lists") { limit, offset ->
            contactsApi.getLists(limit, offset, GetProcessesSortParameter.ASC).let { page ->
                Page(page.count, page.lists.orEmpty().map { it.toTarget(folderNames) })
            }
        }

    private fun <T> page(kind: String, fetch: (Long, Long) -> Page<T>): List<T> {
        val results = mutableListOf<T>()
        var offset = 0L
        while (true) {
            val page = fetchPage(kind) { fetch(PAGE_SIZE, offset) }
            results += page.items
            if (page.items.size < PAGE_SIZE || page.count != null && results.size >= page.count) break
            offset += PAGE_SIZE
        }
        return results
    }

    private fun <T> fetchPage(kind: String, fetch: () -> Page<T>): Page<T> = try {
        fetch()
    } catch (e: RestClientResponseException) {
        if (e.statusCode.value() == 429) log.warn("Brevo target catalog {} fetch was rate limited", kind)
        throw ContactServiceException("Failed to fetch Brevo $kind catalog", e)
    }

    private fun ExternalTarget.matches(query: String): Boolean =
        query.isBlank() ||
            externalId == query ||
            label.lowercase().contains(query) ||
            folderLabel.orEmpty().lowercase().contains(query)

    private fun String.toBrevoId(field: String, operation: String): Long =
        toLongOrNull() ?: throw InvalidExternalIdException("Brevo $operation: $field \"$this\" is not numeric")

    private data class Page<T>(val count: Long?, val items: List<T>)

    companion object {
        const val PAGE_SIZE: Long = 50
        private val log = LoggerFactory.getLogger(BrevoTargetStrategy::class.java)
    }
}

private fun GetLists200ResponseListsInner.toTarget(folderNames: Map<String, String>): ExternalTarget {
    val folderExternalId = folderId?.toString()
    return ExternalTarget(
        system = TargetSystem.BREVO,
        externalId = id.toString(),
        kind = CohortKind.LIST,
        label = name,
        folderLabel = folderExternalId?.let { folderNames[it] },
        memberCount = uniqueSubscribers,
    )
}
