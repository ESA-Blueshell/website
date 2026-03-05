package net.blueshell.api.domain.user.application.contact

/**
 * Domain interface for contact list management with external systems (ADR-019: Anti-Corruption Layer)
 *
 * Adapters are responsible for a single external system. Multiple adapters may be active at the
 * same time; the ContactListService orchestrates fanout across all registered implementations.
 *
 * All IDs are system-specific Longs. The orchestration service resolves domain IDs to system IDs
 * before calling adapter methods.
 */
interface ListSyncAdapter {
    val system: ContactSystem

    /**
     * Creates a list in the external system.
     *
     * @param name The name for the list
     * @param folderName Optional folder/category name
     * @return System-specific list ID
     * @throws ContactServiceException if the operation fails
     */
    fun createList(name: String, folderName: String?): Long

    /**
     * Adds a contact to a list. Both IDs are system-specific.
     *
     * @param systemContactId The system-specific contact ID
     * @param systemListId The system-specific list ID
     * @throws ContactServiceException if the operation fails
     */
    fun addToList(systemContactId: Long, systemListId: Long)

    /**
     * Removes a contact from a list. Both IDs are system-specific.
     *
     * @param systemContactId The system-specific contact ID
     * @param systemListId The system-specific list ID
     * @throws ContactServiceException if the operation fails
     */
    fun removeFromList(systemContactId: Long, systemListId: Long)

    /**
     * Deletes the list from the external system.
     *
     * @param systemListId The system-specific list ID
     * @throws ContactServiceException if the operation fails
     */
    fun deleteList(systemListId: Long)
}
