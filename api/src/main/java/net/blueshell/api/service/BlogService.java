package net.blueshell.api.service;

import net.blueshell.api.model.Blog;
import net.blueshell.api.repository.BlogRepository;
import net.blueshell.api.base.BaseModelService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@org.springframework.stereotype.Service
public class BlogService extends BaseModelService<Blog, UUID, BlogRepository> {

    @Autowired
    public BlogService(BlogRepository blogRepository) {
        super(blogRepository);
    }
}
