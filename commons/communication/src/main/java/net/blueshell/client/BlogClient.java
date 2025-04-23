package net.blueshell.client;

import net.blueshell.dto.InternalBlogDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for net.blueshell.blogservice.controller.BlogController
 */
@FeignClient(
        name = "BlogService",
        contextId = "blogClient",
        path = "/blogs"
)
public interface BlogClient {

    @GetMapping
    List<InternalBlogDTO> findAll();

    @GetMapping("/{id}")
    InternalBlogDTO findById(@PathVariable("id") UUID id);
}

