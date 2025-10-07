package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.BlogDTO;
import net.blueshell.api.dto.FileDTO;
import net.blueshell.api.model.Blog;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class BlogMapper extends BaseMapper<Blog, BlogDTO> {

    @Value("${frontend.url}")
    private String frontendUrl;


    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "title")
    @Mapping(target = "publishedAt")
    public abstract Blog fromDTO(BlogDTO dto, @MappingTarget Blog blog);

    @AfterMapping
    protected void afterFromDTO(BlogDTO dto, @MappingTarget Blog blog) {
        String content = dto.getHtml();
        if (content != null && !content.trim().isEmpty()) {
            Document doc = Jsoup.parse(content);
            doc.select("div:has(a:contains(Unsubscribe))").remove();
            String minifiedHtml = doc.html().replaceAll(">\\s+<", "><").trim();
            blog.setHtml(minifiedHtml);
        }
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "title")
    @Mapping(target = "html")
    @Mapping(target = "publishedAt")
    public abstract BlogDTO toDTO(Blog blog);

    @AfterMapping
    protected void afterToDTO(BlogDTO dto, @MappingTarget Blog blog) {
        dto.setUrl("%s/blogs/%s".formatted(frontendUrl, blog.getId()));
    }

    /**
     * Extracts the title using the "h1.default-heading1" selector.
     * Uses the parsed Document to avoid re-parsing.
     */
    private String extractTitle(Document doc) {
        Element titleElement = doc.selectFirst("h1.default-heading1");
        return titleElement != null ? titleElement.text().trim() : "";
    }

    private List<FileDTO> extractImages(Document doc) {
        List<FileDTO> files = new ArrayList<>();
        for (Element img : doc.select("img")) {
            String src = img.attr("src");
            if (src.isEmpty()) {
                continue;
            }
            FileDTO fileDTO = new FileDTO();
            fileDTO.setUrl(src);
            files.add(fileDTO);
        }
        return files;
    }
}
