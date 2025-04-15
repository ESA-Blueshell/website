package net.blueshell.blogservice;

import net.blueshell.common.communicator.BlogCommunicator;
import net.blueshell.common.dto.BlogDTO;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class BlogController {


    private final BlogMapper blogMapper;
    private final BlogService blogService;

    @Autowired
    public BlogController(BlogService blogService, BlogMapper blogMapper) {
        this.blogMapper = blogMapper;
        this.blogService = blogService;
    }

    @GetMapping("/")
    public String SayHello() {
        return "Blog Service";
    }

    @RabbitListener(queues = "${communicators.blogService.name}")
    public void asyncCreateBlog(BlogDTO blogDTO) {
        Blog blog = blogMapper.fromDTO(blogDTO);
        blogService.create(blog);
    }

    @GetMapping("/blogs")
    public List<BlogDTO> findAll() {
        return blogMapper.toDTOs(blogService.findAll());
    }

    @GetMapping("/blogs/{id}")
    public BlogDTO findById(@PathVariable UUID id) {
        return blogMapper.toDTO(blogService.findById(id));
    }
}