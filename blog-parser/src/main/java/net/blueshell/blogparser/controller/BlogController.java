package net.blueshell.blogparser.controller;

import net.blueshell.blogparser.mapper.InternalBlogMapper;
import net.blueshell.common.communicator.BlogCommunicator;
import net.blueshell.common.dto.BlogDTO;
import net.blueshell.common.dto.InternalBlogDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class BlogController {


    private final BlogCommunicator blogCommunicator;
    private final InternalBlogMapper mapper;

    @Autowired
    public BlogController(BlogCommunicator blogCommunicator, InternalBlogMapper mapper) {
        this.blogCommunicator = blogCommunicator;
        this.mapper = mapper;
    }

    @GetMapping("/blogs")
    public List<BlogDTO> findAll() {
        List<InternalBlogDTO> internalBlogs = blogCommunicator.sendSync("/blogs", HttpMethod.GET, List.class);
        return mapper.toDTOs(internalBlogs);
    }

    @GetMapping("/blogs/{id}")
    public BlogDTO findById(@PathVariable UUID id) {
        InternalBlogDTO internalBlog = blogCommunicator.sendSync("/blogs/" + id, HttpMethod.GET, InternalBlogDTO.class);
        return mapper.toDTO(internalBlog);
    }
}