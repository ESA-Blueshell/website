package net.blueshell.api.shared.job

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Treats a missing entity as permanent rather than transient. A job whose subject
 * has been deleted will never succeed, so retrying it only fills the queue.
 */
inline fun <T> requireExists(block: () -> T): T = try {
    block()
} catch (ex: ResponseStatusException) {
    if (ex.statusCode == HttpStatus.NOT_FOUND) {
        throw NonRetryableJobException(ex.reason ?: "Entity not found", ex)
    }
    throw ex
}
