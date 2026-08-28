package net.blueshell.api.blog.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Exception thrown when attempting to create a blog post with a title that already exists.
 *
 * This exception is automatically mapped to HTTP 409 (Conflict) by Spring's exception handling.
 *
 * Note: This exception is prepared for future use if blog title uniqueness validation is implemented.
 * Currently, the blog domain allows duplicate titles.
 */
class DuplicateBlogTitleException(title: String) :
    ResponseStatusException(HttpStatus.CONFLICT, "Blog with title '$title' already exists")
