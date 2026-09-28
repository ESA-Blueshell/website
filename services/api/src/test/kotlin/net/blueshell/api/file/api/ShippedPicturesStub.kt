package net.blueshell.api.file.api

import net.blueshell.api.file.persistence.File
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

/**
 * A [ShippedPictures] that hands each of [rows], by seed file name, straight to the loader's
 * placement with [picture] as the picture stored for it, so a loader's test is only about where
 * the picture goes.
 */
fun shippedPicturesOf(
    rows: Map<String, List<Map<String, String>>>,
    picture: (Map<String, String>) -> File,
): ShippedPictures =
    mock {
        on { ship(any(), any(), any(), any(), any()) } doAnswer { call ->
            val place = call.getArgument<(Map<String, String>, () -> File) -> Boolean>(4)
            rows[call.getArgument<String>(1)].orEmpty().count { row -> place(row) { picture(row) } }
        }
    }
