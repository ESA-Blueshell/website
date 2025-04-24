package net.blueshell.blogservice.mapper;

import net.blueshell.blogservice.model.Blog;
import net.blueshell.dto.InternalBlogDTO;
import net.blueshell.dto.SocialDTO;
import net.blueshell.enums.PlatformType;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Value;

@Mapper(componentModel = "spring")
public abstract class SocialMapper {

    @Value("${frontend.url}")
    private String frontendUrl;

    public abstract SocialDTO toSocialDTO(Blog blog);

    @AfterMapping
    protected void afterToSocialDTO(Blog blog, @MappingTarget SocialDTO dto) {
        dto.setUrl(frontendUrl + "/blogs" + blog.getId());
        dto.setTitle(blog.getTitle());
        dto.setText(dto.getText());
        PlatformType[] platforms = {PlatformType.FACEBOOK, PlatformType.TWITTER, PlatformType.INSTAGRAM, PlatformType.LINKEDIN};
        dto.setPlatforms(platforms);
    }
}
