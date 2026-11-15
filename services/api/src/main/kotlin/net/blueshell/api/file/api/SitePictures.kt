package net.blueshell.api.file.api

import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.user.api.UserService
import org.slf4j.LoggerFactory
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
    private val users: UserService,
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
    ): File? =
        runCatching {
            apart.execute { files.store(ByteArrayInputStream(bytes), name, mediaType, kind, users.findByUsername(SITE_ACCOUNT)) }
        }.onFailure { log.warn("[site-pictures] {} could not be stored as {}: {}", name, kind, it.message) }
            .getOrNull()

    private companion object {
        val log = LoggerFactory.getLogger(SitePictures::class.java)
        const val SITE_ACCOUNT = "system"
    }
}
