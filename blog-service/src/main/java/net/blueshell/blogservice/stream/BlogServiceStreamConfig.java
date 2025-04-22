package net.blueshell.blogservice.stream;

import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.blogservice.model.Blog;
import net.blueshell.common.dto.InternalBlogDTO;
import net.blueshell.blogservice.mapper.BlogMapper;
import net.blueshell.blogservice.service.BlogService;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BlogServiceStreamConfig {

    private final BlogMapper blogMapper;
    private final BlogService blogService;
    private final StreamBridge streamBridge;

    public BlogServiceStreamConfig(BlogMapper blogMapper, BlogService blogService, StreamBridge streamBridge) {
        this.blogMapper = blogMapper;
        this.blogService = blogService;
        this.streamBridge = streamBridge;
    }

    @Bean
    public Consumer<InternalBlogDTO> asyncCreateBlog() {
        return internalBlogDTO -> {
            Blog blog = blogMapper.fromDTO(internalBlogDTO);
            blogService.create(blog);
            InternalBlogDTO blogDTO = blogMapper.toDTO(blog);
            streamBridge.send("blog-out-0", blogDTO);
        };
    }
}
