package net.blueshell.api.cohort.domain

import net.blueshell.api.contact.api.ContactListAdapter
import net.blueshell.api.contact.api.ContactServiceException

/**
 * Brevo's folders. Brevo lets two carry one name, and removes a folder's lists with it, so a folder
 * is only ever removed after the lists are read again and it holds none.
 */
class BrevoFolders(
    private val lists: ContactListAdapter,
) : FolderKeeper {
    override fun states(): List<FolderState> {
        val held = lists.listAll().groupingBy { it.folderId }.eachCount()
        return lists
            .listFolders()
            .map { (id, name) -> FolderState(id.toString(), name, held[id] ?: 0) }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.id.toLong() }))
    }

    override fun merge(): FolderMerge {
        val copies =
            lists
                .listFolders()
                .entries
                .groupBy({ it.value.lowercase() }, { it.key })
                .values
                .filter { it.size > 1 }
        var moved = 0
        var removed = 0
        copies.forEach { ids ->
            val kept = ids.min()
            val others = ids - kept
            lists.listAll().filter { it.folderId in others }.forEach {
                lists.moveList(it.externalListId, kept)
                moved++
            }
            val holding = lists.listAll().mapNotNull { it.folderId }.toSet()
            others.filter { it !in holding }.forEach {
                lists.deleteFolder(it)
                removed++
            }
        }
        return FolderMerge(removed, moved)
    }

    override fun remove(id: String) {
        val folderId = id.toLongOrNull() ?: throw ContactServiceException("Brevo has no folder $id")
        if (lists.listAll().any { it.folderId == folderId }) throw ContactServiceException("The folder still holds lists")
        lists.deleteFolder(folderId)
    }
}
