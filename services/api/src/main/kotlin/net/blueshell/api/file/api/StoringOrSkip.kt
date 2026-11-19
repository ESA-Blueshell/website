package net.blueshell.api.file.api

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("net.blueshell.api.file.api.SitePictures")

/**
 * What [store] answers, or null where it threw, which is logged for [what]. A picture the site
 * stores by itself is a nicety: the record it was for stands without it.
 */
@Suppress("TooGenericExceptionCaught") // Any failure skips this one picture; none may fail the caller.
internal fun <T> storingOrSkip(
    what: String,
    store: () -> T,
): T? =
    try {
        store()
    } catch (e: Exception) {
        log.warn("[site-pictures] could not store {}: {}", what, e.message)
        null
    }
