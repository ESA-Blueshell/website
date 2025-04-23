package net.blueshell.blogservice.mapper;

import net.blueshell.blogservice.model.Blog;
import net.blueshell.common.dto.InternalBlogDTO;
import net.blueshell.common.dto.SocialDTO;
import net.blueshell.common.enums.PlatformType;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Value;

@Mapper(componentModel = "spring")
public abstract class SocialMapper {

    @Value("${frontend.url}")
    private String frontendUrl;

    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    public abstract SocialDTO toSocialDTO(Blog blog);

    @AfterMapping
    protected void afterToSocialDTO(Blog blog, @MappingTarget SocialDTO dto) {
        dto.setUrl(frontendUrl + "/blogs" + blog.getId());
        dto.setTitle(blog.getTitle());
        dto.setText(dto.getText());
        PlatformType[] platforms = {PlatformType.FACEBOOK, PlatformType.TWITTER, PlatformType.INSTAGRAM};
        dto.setPlatforms(platforms);
    }
}
