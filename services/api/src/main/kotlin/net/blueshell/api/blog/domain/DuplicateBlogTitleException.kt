package net.blueshell.api.blog.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * A blog post whose title is already taken, answered as a 409.
 *
 * Nothing raises this: the blog domain admits duplicate titles, and no uniqueness rule has been
 * written.
 */
class DuplicateBlogTitleException(title: String) :
    ResponseStatusException(HttpStatus.CONFLICT, "Blog with title '$title' already exists")
