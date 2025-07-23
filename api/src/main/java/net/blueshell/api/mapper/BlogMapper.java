package net.blueshell.api.mapper;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.BlogDTO;
import net.blueshell.api.dto.FileDTO;
import net.blueshell.api.model.Blog;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public abstract class BlogMapper extends BaseMapper<Blog, BlogDTO> {

    @Value("${frontend.url}")
    private String frontendUrl;


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "text", ignore = true)
    @Mapping(target = "html", ignore = true)
    @Mapping(target = "markdown", ignore = true)
    public abstract Blog fromDTO(BlogDTO dto);

    @AfterMapping
    protected void afterFromDTO(BlogDTO dto, @MappingTarget Blog blog) {
        String content = dto.getHtml();
        if (content != null && !content.trim().isEmpty()) {
            Document doc = Jsoup.parse(content);

            // Remove unwanted elements: any divs containing an unsubscribe link
            // (adjust the selector as needed if the structure differs).
            doc.select("div:has(a:contains(Unsubscribe))").remove();

            // Extract plain text from the updated document.
            String plainText = doc.select("body").text();
            String title = extractTitle(doc);

            // Minify HTML: remove extra whitespace between HTML tags,
            // replacing ">\s+<" with "><". This preserves necessary spacing within text nodes.
            String minifiedHtml = doc.html().replaceAll(">\\s+<", "><").trim();

            blog.setTitle(title);
            blog.setHtml(minifiedHtml);
            blog.setText(plainText);
        }
    }

    @Mapping(target = "url", ignore = true)
    public abstract BlogDTO toDTO(Blog blog);

    @AfterMapping
    protected void afterToDTO(BlogDTO dto, @MappingTarget Blog blog) {
        dto.setUrl(frontendUrl + "/blogs/" + blog.getId());
    }

    private String generateMarkdown(String content) {
        FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder().build();
        return converter.convert(content);
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
            String alt = img.attr("alt");
            if (src.isEmpty()) {
                continue;
            }
            FileDTO fileDTO = new FileDTO();
            fileDTO.setUrl(src);
            fileDTO.setFileName(alt);
            files.add(fileDTO);
        }
        return files;
    }
}
