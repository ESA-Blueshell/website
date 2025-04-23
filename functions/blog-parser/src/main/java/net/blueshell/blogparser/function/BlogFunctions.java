package net.blueshell.blogparser.function;

import net.blueshell.common.client.BlogClient;
import net.blueshell.blogparser.mapper.InternalBlogMapper;
import net.blueshell.common.dto.BlogDTO;
import net.blueshell.common.dto.InternalBlogDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

@Configuration
public class BlogFunctions {

    private final InternalBlogMapper mapper;
    private final BlogClient blogClient;

    public BlogFunctions(InternalBlogMapper mapper, BlogClient blogClient) {
        this.mapper = mapper;
        this.blogClient = blogClient;
    }

    /**
     * GET /blogs
     * When deployed with spring-cloud-function-web,
     * HTTP GET to “/blogs” will invoke this Supplier.
     */
    @Bean
    public Supplier<List<BlogDTO>> blogs() {
        return () -> {
            List<InternalBlogDTO> internals = blogClient.findAll();
            return mapper.fromInternals(internals);
        };
    }

    /**
     * GET /blogsById
     * Expects a JSON body containing the UUID string, or
     * with spring-cloud-function-web you can also do:
     *   GET /blogsById?input=<uuid>
     */
    @Bean
    public Function<String, BlogDTO> blogById() {
        return idString -> {
            UUID id = UUID.fromString(idString);
            InternalBlogDTO internal = blogClient.findById(id);
            return mapper.fromInternal(internal);
        };
    }
}
