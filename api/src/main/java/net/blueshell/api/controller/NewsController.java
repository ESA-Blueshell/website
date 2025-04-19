package net.blueshell.api.controller;

import jakarta.validation.Valid;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.NewsDTO;
import net.blueshell.api.mapper.NewsMapper;
import net.blueshell.api.model.News;
import net.blueshell.api.service.NewsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class NewsController extends BaseController<NewsService, NewsMapper> {

    @Autowired
    public NewsController(NewsService service, NewsMapper mapper) {
        super(service, mapper);
    }

    @GetMapping(value = "/newsPageable")
    public Page<NewsDTO> newsPageable(Pageable pageable) {
        Page<News> newsPage = service.findAll(pageable);
        return mapper.toDTOs(newsPage);
    }

    @GetMapping(value = "/news")
    public List<NewsDTO> getNews() {
        List<News> allNews = service.findAll();
        // Reverse order: since from() doesn't change order, we can just reverse after mapping
        List<NewsDTO> dtoList = mapper.toDTOs(allNews);
        // Reverse the list
        for (int i = 0, j = dtoList.size() - 1; i < j; i++) {
            dtoList.add(i, dtoList.remove(j));
        }
        return dtoList;
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping(value = "/news")
    public NewsDTO createNews(@Valid @RequestBody NewsDTO newsDTO) {
        News news = mapper.fromDTO(newsDTO);
        service.create(news);
        return mapper.toDTO(news);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping(value = "/news/{id}")
    public NewsDTO createOrUpdateNews(@PathVariable("id") Long id, @Valid @RequestBody NewsDTO newsDTO) {
        try {
            News news = mapper.fromDTO(newsDTO);
            news.setId(id);
            service.update(news);
            return mapper.toDTO(news);
        } catch (Exception e) {
            // If not found, let's create new if BOARD user
            if (hasAuthority(Role.BOARD)) {
                News news = mapper.fromDTO(newsDTO);
                service.create(news);
                return mapper.toDTO(news);
            } else {
                throw e;
            }
        }
    }

    @GetMapping(value = "/news/{id}")
    public NewsDTO getNewsById(@PathVariable(name = "id") String id) {
        News news = service.findById(Long.parseLong(id));
        return mapper.toDTO(news);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping(value = "/news/{id}")
    public void deleteNewsById(@PathVariable("id") String id) {
        service.deleteById(Long.parseLong(id));
    }
}
