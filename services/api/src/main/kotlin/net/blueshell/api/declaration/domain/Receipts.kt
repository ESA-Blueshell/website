package net.blueshell.api.declaration.domain

import net.blueshell.api.declaration.persistence.Declaration
import net.blueshell.api.declaration.persistence.DeclarationRepository
import net.blueshell.api.declaration.persistence.DeclarationState
import net.blueshell.api.file.api.FileService
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream

/**
 * Receipts as private files: stored on upload, opened only by their submitter and the board, and
 * deleted once no declaration holds them. A receipt is kept apart per uploader, so the person who
 * uploaded it is the one whose declaration may hold it.
 */
@Component
class Receipts(
    private val files: FileService,
    private val declarations: DeclarationRepository,
) {
    @Transactional
    fun store(upload: MultipartFile): File = files.storeMultipart(upload, FileType.RECEIPT)

    @Transactional(readOnly = true)
    fun mayAttach(
        userId: Long,
        fileId: Long,
    ): Boolean = receipt(fileId)?.uploaderId == userId

    /** The receipt for [reader] to open; a board member opens what reached the board, never a draft only its writer saw. */
    @Transactional(readOnly = true)
    fun open(
        reader: CurrentUser,
        fileId: Long,
    ): File {
        val file = receipt(fileId) ?: throw ReceiptNotFound()
        val board = Role.BOARD in reader.roles
        val allowed = file.uploaderId == reader.id || (board && declarations.findAllHoldingReceipt(fileId).any { it.reachedBoard })
        if (!allowed) throw ReceiptNotFound()
        return file
    }

    /** The bytes of a receipt [open] let through. The caller closes the stream. */
    fun contentOf(file: File): InputStream = files.openContent(file)

    /** The stored files behind [fileIds], for a response to name. */
    @Transactional(readOnly = true)
    fun describe(fileIds: Collection<Long>): Map<Long, File> = fileIds.distinct().mapNotNull { id -> receipt(id)?.let { id to it } }.toMap()

    /** Deletes each of [fileIds] that no declaration holds any more. */
    @Transactional
    fun release(fileIds: Collection<Long>) {
        fileIds.distinct().forEach { id ->
            if (declarations.findAllHoldingReceipt(id).isEmpty()) receipt(id)?.let(files::delete)
        }
    }

    private fun receipt(id: Long): File? = files.findStored(id, FileType.RECEIPT)

    private val Declaration.reachedBoard: Boolean
        get() = steps.any { it.state == DeclarationState.SUBMITTED }
}
