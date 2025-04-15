package net.blueshell.blogservice;

import net.blueshell.common.dto.BlogDTO;
import net.blueshell.db.BaseMapper;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Value;

import java.sql.Timestamp;
import java.time.Instant;

@Mapper(componentModel = "spring")
public abstract class BlogMapper extends BaseMapper<Blog, BlogDTO> {

    @Value("${frontend.url}")
    private String frontendUrl;

    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    public abstract Blog fromDTO(BlogDTO dto);

    @AfterMapping
    protected void afterFromDTO(BlogDTO dto, @MappingTarget Blog blog) {
        if (blog.getCreatedAt() == null) {
            blog.setCreatedAt(Timestamp.from(Instant.now()));
        }
    }

    @Mapping(target = "url", ignore = true)
    public abstract BlogDTO toDTO(Blog blog);

    @AfterMapping
    protected void afterToDTO(BlogDTO dto, @MappingTarget Blog blog) {
        dto.setUrl(frontendUrl + "/blogs/" + blog.getId());
    }
}
