package net.blueshell.api.mapper;

import net.blueshell.api.model.Blog;
import net.blueshell.api.dto.SocialDTO;
import net.blueshell.api.common.enums.PlatformType;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
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
