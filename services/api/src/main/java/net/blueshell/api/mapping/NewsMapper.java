package net.blueshell.api.mapping;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.NewsDTO;
import net.blueshell.api.model.News;
import net.blueshell.api.model.User;
import net.blueshell.api.service.UserService;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;

@Mapper(componentModel = "spring")
public abstract class NewsMapper extends BaseMapper<News, NewsDTO> {

    @Autowired
    private UserService userService;

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "lastEditor", ignore = true)
    @Mapping(target = "postedAt", ignore = true)
    public abstract News fromDTO(NewsDTO dto);

    @AfterMapping
    protected void afterFromDTO(NewsDTO dto, @MappingTarget News news) {
        User author = userService.findById(Long.parseLong(dto.getCreatorId()));
        User lastEditor = userService.findById(Long.parseLong(dto.getLastEditorId()));

        news.setAuthor(author);
        news.setLastEditor(lastEditor);

        if (dto.getPostedAt() != null) {
            news.setPostedAt(Timestamp.valueOf(dto.getPostedAt()));
        }
        news.setNewsType(dto.getNewsType());
    }

    @Mapping(target = "id", expression = "java(String.valueOf(news.getId()))")
    @Mapping(target = "creatorId", expression = "java(String.valueOf(news.getAuthorId()))")
    @Mapping(target = "creatorUsername", expression = "java(news.getAuthor() == null ? null : news.getAuthor().getUsername())")
    @Mapping(target = "lastEditorId", expression = "java(String.valueOf(news.getLastEditorId()))")
    @Mapping(target = "lastEditorUsername", expression = "java(news.getLastEditor() == null ? null : news.getLastEditor().getUsername())")
    @Mapping(target = "newsType", source = "news.newsType")
    @Mapping(target = "title", source = "news.title")
    @Mapping(target = "content", source = "news.content")
    @Mapping(target = "postedAt", expression = "java(news.getPostedAt() == null ? null : news.getPostedAt().toString())")
    public abstract NewsDTO toDTO(News news);
}
