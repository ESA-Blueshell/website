package net.blueshell.api.blog.application

import net.blueshell.api.blog.persistence.Blog
import net.blueshell.api.blog.persistence.repository.BlogRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class BlogService @Autowired constructor(blogRepository: BlogRepository, events: ApplicationEventPublisher) :
    BaseModelService<Blog, Long, BlogRepository>(blogRepository)
