package net.blueshell.blogservice;

import net.blueshell.db.BaseMapper;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.sql.Timestamp;
import java.time.Instant;

@Mapper(componentModel = "spring")
public abstract class BlogMapper extends BaseMapper<Blog, BlogDTO> {

    public abstract Blog fromDTO(BlogDTO dto);

    @AfterMapping
    protected void afterFromDTO(BlogDTO dto, @MappingTarget Blog blog) {
        if (blog.getCreatedAt() == null) {
            blog.setCreatedAt(Timestamp.from(Instant.now()));
        }
    }

    public abstract BlogDTO toDTO(Blog blog);
}
