package net.blueshell.api.blog.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Exception thrown when a requested blog post cannot be found.
 *
 * This exception is automatically mapped to HTTP 404 (Not Found) by Spring's exception handling.
 */
class BlogNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Blog with id $id not found")
