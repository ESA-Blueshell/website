package net.blueshell.client;

import net.blueshell.dto.NewsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(
        name = "API",
        contextId = "newsClient",
        path="/news"
)
public interface NewsClient {

    @GetMapping("Pageable")
    Page<NewsDTO> newsPageable(@SpringQueryMap Pageable pageable);

    @GetMapping
    List<NewsDTO> getNews();

    @PostMapping
    NewsDTO createNews(@RequestBody NewsDTO newsDTO);

    @PutMapping("/{id}")
    NewsDTO createOrUpdateNews(@PathVariable("id") Long id,
                               @RequestBody NewsDTO newsDTO);

    @GetMapping("/{id}")
    NewsDTO getNewsById(@PathVariable("id") String id);

    @DeleteMapping("/{id}")
    void deleteNewsById(@PathVariable("id") String id);
}
