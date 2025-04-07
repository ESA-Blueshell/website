package net.blueshell.blogservice;

import net.blueshell.common.communication.IAsyncCommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import net.blueshell.common.communication.communicators.BlogCommunicator;
import net.blueshell.common.communication.communicators.EmailParserCommunicator;
import net.blueshell.common.dto.BlogDTO;
import net.blueshell.common.dto.EmailDTO;
import net.blueshell.db.BaseController;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BlogController {


    private final BlogMapper blogMapper;
    private final BlogService blogService;
    private final ICommunicationService communicationService;
    private final IAsyncCommunicationService asyncCommunicationService;

    @Autowired
    public BlogController(BlogService blogService, BlogMapper blogMapper, ICommunicationService communicationService, IAsyncCommunicationService asyncCommunicationService) {
        this.blogMapper = blogMapper;
        this.blogService = blogService;
        this.communicationService = communicationService;
        this.asyncCommunicationService = asyncCommunicationService;
    }

    @GetMapping("/")
    public String SayHello() {
        return "Blog Service";
    }

//    @RabbitListener(queues = BlogCommunicator.name)
//    public void asyncCreateBlog(String blogDTO) {
//        System.out.println("blogDTO:" + blogDTO);
//    }

    @RabbitListener(queues = BlogCommunicator.name)
    public void asyncCreateBlog(BlogDTO blogDTO) {
        System.out.println("blogDTO:" + blogDTO);
//        Blog blog = blogMapper.fromDTO(blogDTO);
//        blogService.create(blog);
//        System.out.println("Created blog: " + blog.getId());
    }

    @GetMapping("/blogs")
    public List<BlogDTO> findAll() {
        return blogMapper.toDTOs(blogService.findAll());
    }
}