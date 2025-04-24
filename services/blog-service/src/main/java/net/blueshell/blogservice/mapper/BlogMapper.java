package net.blueshell.blogservice.mapper;

import net.blueshell.blogservice.model.Blog;
import net.blueshell.dto.InternalBlogDTO;
import net.blueshell.mapper.BaseMapper;
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
