package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.Blog;
import net.blueshell.api.repository.BlogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class BlogService extends BaseModelService<Blog, BlogRepository> {

    @Autowired
    public BlogService(BlogRepository blogRepository, ApplicationEventPublisher events) {
        super(blogRepository);
    }
}
