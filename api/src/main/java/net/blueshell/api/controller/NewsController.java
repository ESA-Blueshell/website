package net.blueshell.api.controller;

import jakarta.validation.Valid;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.NewsDTO;
import net.blueshell.api.mapper.NewsMapper;
import net.blueshell.api.model.News;
import net.blueshell.api.service.NewsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/news")
public class NewsController extends BaseController<NewsService, NewsMapper> {

    @Autowired
    public NewsController(NewsService service, NewsMapper mapper) {
        super(service, mapper);
    }

    /**
     * Return either a full list <b>or</b> a paged slice depending on whether
     * {@code page} or {@code size} are supplied.
     * Examples:
     * <ul>
     *   <li><code>GET /news</code> &nbsp;&rarr;&nbsp; <i>all items</i></li>
     *   <li><code>GET /news?page=0&amp;size=20</code> &nbsp;&rarr;&nbsp; <i>paged</i></li>
     * </ul>
     */
    @GetMapping
    public Object getNews(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "perPage", required = false) Integer size,
            Pageable pageable) {

        /* When either param is present, use Spring-Data paging */
        if (page != null || size != null) {
            Pageable effective = PageRequest.of(
                    page != null ? page : pageable.getPageNumber(),
                    size != null ? size : pageable.getPageSize(),
                    pageable.getSort()
            );
            return mapper.toDTOs(service.findAll(effective));
        }

        /* Otherwise return the complete list (latest first) */
        return mapper.toDTOs(service.findAll()).reversed();
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping
    public NewsDTO createNews(@Valid @RequestBody NewsDTO newsDTO) {
        News news = mapper.fromDTO(newsDTO);
        service.create(news);
        return mapper.toDTO(news);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping("/{id}")
    public NewsDTO createOrUpdateNews(@PathVariable Long id,
                                      @Valid @RequestBody NewsDTO newsDTO) {
        try {
            News news = mapper.fromDTO(newsDTO);
            news.setId(id);
            service.update(news);
            return mapper.toDTO(news);
        } catch (Exception ex) {
            if (hasAuthority(Role.BOARD)) {
                News news = mapper.fromDTO(newsDTO);
                service.create(news);
                return mapper.toDTO(news);
            }
            throw ex;
        }
    }

    @GetMapping("/{id}")
    public NewsDTO getNewsById(@PathVariable Long id) {
        return mapper.toDTO(service.findById(id));
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNewsById(@PathVariable Long id) {
        service.delete(id);
    }
}
