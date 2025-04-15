package net.blueshell.emailparser;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import net.blueshell.common.Event;
import net.blueshell.common.Image;
import net.blueshell.common.communication.CommunicationService;
import net.blueshell.common.communication.IAsyncCommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import net.blueshell.common.dto.BlogDTO;
import net.blueshell.common.dto.EmailDTO;
import net.blueshell.common.dto.FileDTO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public abstract class EmailMapper {

    @Autowired
    private ICommunicationService communicationService;

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "text", ignore = true)
    @Mapping(target = "html", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "markdown", ignore = true)
    public abstract BlogDTO toBlogDTO(EmailDTO dto);

    /**
     * After the basic mapping is done, parse the HTML from EmailDTO
     * and set the text, html, and images on the BlogDTO.
     */
    @AfterMapping
    protected void afterToBlogDTO(EmailDTO emailDTO, @MappingTarget BlogDTO blogDTO) {
        String content = emailDTO.getHtml();
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

            blogDTO.setTitle(title);
            blogDTO.setHtml(minifiedHtml);
            blogDTO.setText(plainText);
        }
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
        // Alternatively, send these to your file service:
        // return communicationService.sendToFileService("/files", HttpMethod.PUT, String.class);
    }
}
