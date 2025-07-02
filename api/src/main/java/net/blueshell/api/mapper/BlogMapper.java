package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.InternalBlogDTO;
import net.blueshell.api.model.Blog;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Value;

import java.sql.Timestamp;
import java.time.Instant;

@Mapper(componentModel = "spring")
public abstract class BlogMapper extends BaseMapper<Blog, InternalBlogDTO> {

    @Value("${frontend.url}")
    private String frontendUrl;

    public abstract Blog fromDTO(InternalBlogDTO dto);

    @AfterMapping
    protected void afterFromDTO(InternalBlogDTO dto, @MappingTarget Blog blog) {
        if (blog.getCreatedAt() == null) {
            blog.setCreatedAt(Timestamp.from(Instant.now()));
        }
    }

    @Mapping(target = "url", ignore = true)
    public abstract InternalBlogDTO toDTO(Blog blog);

    @AfterMapping
    protected void afterToDTO(InternalBlogDTO dto, @MappingTarget Blog blog) {
        dto.setUrl(frontendUrl + "/blogs/" + blog.getId());
    }
}
