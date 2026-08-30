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
        HttpStatus.CONTENT_TOO_LARGE,
        "A ${type.name.lowercase().replace('_', ' ')} may be at most ${maxBytes / (1024 * 1024)} MB",
    )

/**
 * A picture meant to be seen was asked for, and the kind named is not one.
 *
 * The endpoint that stores public pictures admits only kinds that are publicly readable, so it
 * can never be used to stash a private document behind a route anybody can fetch from. Named
 * rather than silently stored somewhere else: a caller asking it to hold a document has
 * misunderstood which endpoint it wanted.
 */
class NotAPublicImageException(type: FileType) :
    ResponseStatusException(HttpStatus.BAD_REQUEST, "$type is not a kind of file that pages draw")
