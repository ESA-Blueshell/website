package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.contact.api.ContactListAdapter
import net.blueshell.api.contact.api.ContactListRef
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.stereotype.Service

/**
 * Brevo's [TargetStrategy], stated entirely in terms of [ContactListAdapter]: the catalogue, the
 * folders and the membership writes are all that module's, so nothing Brevo-shaped reaches here
 * beyond the numeric id its lists are keyed by (API ADR-019).
 */
@Service
class BrevoTargetStrategy(
    contactListAdapters: List<ContactListAdapter>,
) : TargetStrategy {
    private val lists = contactListAdapters.single { it.system == TargetSystem.BREVO }

    override val descriptor =
        TargetDescriptor(
            system = TargetSystem.BREVO,
            kind = TargetKind.LIST,
        )

    override fun catalog(query: String?): List<ExternalTarget> {
        val q = query?.trim()?.lowercase().orEmpty()
        val folderNames = lists.listFolders()
        return lists
            .listAll()
            .map { it.toTarget(folderNames[it.folderId]) }
            .filter { it.matches(q) }
            .sortedWith(compareBy({ it.folderLabel.orEmpty() }, { it.label }))
    }

    override fun create(
        label: String,
        folder: String?,
    ): ExternalTarget =
        ExternalTarget(
            system = system,
            externalId = lists.createList(label, folder).toString(),
            kind = descriptor.kind,
            label = label,
            folderLabel = folder,
            path = pathTo(folder),
        )

    override fun members(external: ExternalTarget): List<ExternalMember> =
        lists
            .listMembers(external.externalId.toBrevoId("externalId", "members"))
            .map { ExternalMember(it.externalUserId.toString(), it.email) }
            .filter { it.externalUserId.isNotBlank() }

    override fun add(
        external: ExternalTarget,
        externalUserId: String,
    ) {
        lists.addToList(
            externalUserId.toBrevoId("externalUserId", "add"),
            external.externalId.toBrevoId("externalId", "add"),
        )
    }

    override fun remove(
        external: ExternalTarget,
        externalUserId: String,
    ) {
        lists.removeFromList(
            externalUserId.toBrevoId("externalUserId", "remove"),
            external.externalId.toBrevoId("externalId", "remove"),
        )
    }

    /** Brevo's own folders, including the ones holding nothing. */
    override fun folders(): List<String> = lists.listFolders().values.sorted()

    override fun move(
        external: ExternalTarget,
        folder: String,
    ): ExternalTarget {
        // Brevo files by folder id, so a name has to name a folder that exists. Refusing an
        // unknown one beats silently creating a near-duplicate of a folder already there.
        val folderId =
            lists
                .listFolders()
                .entries
                .firstOrNull { it.value.equals(folder, ignoreCase = true) }
                ?.key
                ?: throw IllegalArgumentException("No folder named '$folder'")

        lists.moveList(external.externalId.toBrevoId("externalId", "move"), folderId)
        return external.copy(folderLabel = folder, path = pathTo(folder))
    }

    override fun rename(
        external: ExternalTarget,
        name: String,
    ): ExternalTarget {
        lists.renameList(external.externalId.toBrevoId("externalId", "rename"), name)
        return external.copy(label = name)
    }

    override fun createFolder(name: String): List<String> {
        lists.createFolder(name)
        return folders()
    }

    override fun delete(external: ExternalTarget) {
        lists.deleteList(external.externalId.toBrevoId("externalId", "delete"))
    }

    /**
     * Brevo files a list in at most one folder, so a path here is the system and, when there
     * is one, the folder it sits in.
     */
    private fun pathTo(folder: String?): List<String> = listOfNotNull(TargetSystem.BREVO.shownName, folder?.takeIf { it.isNotBlank() })

    private fun ContactListRef.toTarget(folder: String?): ExternalTarget =
        ExternalTarget(
            system = system,
            externalId = externalListId.toString(),
            kind = descriptor.kind,
            label = name,
            folderLabel = folder,
            memberCount = memberCount,
            path = pathTo(folder),
        )

    private fun ExternalTarget.matches(query: String): Boolean =
        query.isBlank() ||
            externalId == query ||
            label.lowercase().contains(query) ||
            folderLabel.orEmpty().lowercase().contains(query)

    /**
     * Brevo keys lists and contacts by number, so an id that is not one names nothing there.
     * The sole conversion between the port's [String] ids and Brevo's own.
     */
    private fun String.toBrevoId(
        field: String,
        operation: String,
    ): Long = toLongOrNull() ?: throw InvalidExternalIdException("Brevo $operation: $field \"$this\" is not numeric")
}
