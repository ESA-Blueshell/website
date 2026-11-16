package net.blueshell.api.file.api

import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import org.springframework.stereotype.Component
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.io.ByteArrayInputStream

/**
 * Stores a picture the site fetched itself, such as a cover copied from another service, credited
 * to the site's own account rather than to whoever caused the fetch. Null where it cannot be
 * stored: a copy is a nicety, and the record it was for is kept without it.
 *
 * The store runs in a transaction of its own. In the caller's, a refused picture would mark the
 * caller's transaction for rollback even once caught, and take the record down with it.
 */
@Component
class SitePictures(
    private val files: FileService,
    private val site: SiteAccount,
    transactions: TransactionTemplate,
) {
    private val apart =
        TransactionTemplate(requireNotNull(transactions.transactionManager)).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    fun store(
        bytes: ByteArray,
        name: String,
        mediaType: String,
        kind: FileType,
    ): File? {
        val owner = site.orNull(name) ?: return null
        return site.attempt(name) { apart.execute { files.store(ByteArrayInputStream(bytes), name, mediaType, kind, owner) } }
    }
}
