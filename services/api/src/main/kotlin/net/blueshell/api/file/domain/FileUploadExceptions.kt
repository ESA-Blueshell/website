package net.blueshell.api.file.domain

import net.blueshell.api.shared.enums.FileType
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * The kind of file being uploaded does not admit what was sent.
 *
 * Enforced where the file is stored rather than only at the endpoint that takes it, so a kind's
 * rules hold for every way in rather than for the ones somebody remembered to annotate.
 */
class UnsupportedMediaTypeException(type: FileType, mediaType: String) :
    ResponseStatusException(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "A ${type.name.lowercase().replace('_', ' ')} cannot be a $mediaType",
    )

class FileTooLargeException(type: FileType, maxBytes: Long) :
    ResponseStatusException(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "A ${type.name.lowercase().replace('_', ' ')} may be at most ${maxBytes / (1024 * 1024)} MB",
    )
