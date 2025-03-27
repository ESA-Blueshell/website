package net.blueshell.blogservice;

import net.blueshell.db.BaseModelService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@org.springframework.stereotype.Service
public class BlogService extends BaseModelService<Blog, UUID, BlogRepository> {

    @Autowired
    public BlogService(BlogRepository blogRepository) {
        super(blogRepository);
    }
}
