package net.blueshell.api.service

import net.blueshell.api.model.Blog
import net.blueshell.api.repository.BlogRepository
import net.blueshell.api.service.base.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class BlogService @Autowired constructor(blogRepository: BlogRepository, events: ApplicationEventPublisher) :
    BaseModelService<Blog, Long, BlogRepository>(blogRepository)
