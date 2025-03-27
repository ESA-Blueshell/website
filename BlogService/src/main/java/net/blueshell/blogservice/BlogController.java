package net.blueshell.blogservice;

import net.blueshell.db.BaseController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BlogController extends BaseController<BlogService, BlogMapper> {

    @Autowired
    public BlogController(BlogService service, BlogMapper mapper) {
        super(service, mapper);
    }

    @GetMapping("/")
    public String SayHello() {
        return "Blog Service";
    }
}