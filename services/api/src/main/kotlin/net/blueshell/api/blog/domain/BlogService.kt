package net.blueshell.api.blog.domain

import net.blueshell.api.blog.persistence.Blog
import net.blueshell.api.blog.persistence.BlogRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BlogService @Autowired constructor(blogRepository: BlogRepository, events: ApplicationEventPublisher) :
    BaseModelService<Blog, Long, BlogRepository>(blogRepository) {

    /**
     * Find a blog by its ID.
     *
     * @throws BlogNotFoundException if the blog does not exist
     */
    @Transactional(readOnly = true)
    override fun findById(id: Long): Blog {
        return repository.findById(id).orElseThrow { BlogNotFoundException(id) }
    }

    /**
     * Delete a blog by its ID.
     *
     * @throws BlogNotFoundException if the blog does not exist
     */
    @Transactional
    override fun deleteById(id: Long) {
        if (!repository.existsById(id)) {
            throw BlogNotFoundException(id)
        }
        repository.deleteById(id)
    }
}
