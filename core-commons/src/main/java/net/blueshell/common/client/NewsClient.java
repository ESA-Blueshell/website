package net.blueshell.common.client;

import net.blueshell.common.dto.NewsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "API")
public interface NewsClient {

    @GetMapping("/newsPageable")
    Page<NewsDTO> newsPageable(@SpringQueryMap Pageable pageable);

    @GetMapping("/news")
    List<NewsDTO> getNews();

    @PostMapping("/news")
    NewsDTO createNews(@RequestBody NewsDTO newsDTO);

    @PutMapping("/news/{id}")
    NewsDTO createOrUpdateNews(@PathVariable("id") Long id,
                               @RequestBody NewsDTO newsDTO);

    @GetMapping("/news/{id}")
    NewsDTO getNewsById(@PathVariable("id") String id);

    @DeleteMapping("/news/{id}")
    void deleteNewsById(@PathVariable("id") String id);
}
