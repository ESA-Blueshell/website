package net.blueshell.api.feature.blog.service

import net.blueshell.api.feature.blog.model.Blog
import net.blueshell.api.feature.blog.repository.BlogRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class BlogService @Autowired constructor(blogRepository: BlogRepository, events: ApplicationEventPublisher) :
    BaseModelService<Blog, Long, BlogRepository>(blogRepository)
