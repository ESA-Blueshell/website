package net.blueshell.api.service;

import net.blueshell.api.model.News;
import net.blueshell.api.repository.NewsRepository;
import net.blueshell.api.base.BaseModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NewsService extends BaseModelService<News, Long, NewsRepository> {

    @Autowired
    public NewsService(NewsRepository repository) {
        super(repository);
    }
}
