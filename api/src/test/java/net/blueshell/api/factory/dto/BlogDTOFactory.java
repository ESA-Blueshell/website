package net.blueshell.api.factory.dto;

import net.blueshell.api.dto.BlogDTO;
import org.springframework.stereotype.Component;

/**
 * Factory for BlogDTO test instances.
 */
@Component
public class BlogDTOFactory extends BaseDtoFactory<BlogDTO> {

    @Override
    public Class<BlogDTO> targetType() {
        return BlogDTO.class;
    }

    @Override
    public BlogDTO createBasic() {
        BlogDTO dto = new BlogDTO();
        dto.setTitle(unique("Blog Title"));
        dto.setHtml("<p>Test content</p>");
        dto.setPublishedAt(now());
        dto.setUrl("https://example.com/blog/" + nextId());
        return dto;
    }
}
