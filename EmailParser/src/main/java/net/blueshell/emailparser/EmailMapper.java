package net.blueshell.emailparser;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import net.blueshell.common.Event;
import net.blueshell.common.Image;
import net.blueshell.common.communication.CommunicationService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public abstract class EmailMapper {

    @Autowired
    private CommunicationService communicationService;

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
            String markdown = generateMarkdown(content);
            Document doc = Jsoup.parse(content);
            String plainText = doc.select("body").text();
//            List<Image> images = extractImages(doc);

            blogDTO.setMarkdown(markdown);
            blogDTO.setHtml(content);
//            blogDTO.setImages(images);
            blogDTO.setText(plainText);

        }
    }

    private String generateMarkdown(String content) {
        FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder().build();
        return converter.convert(content);
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
//        return communicationService.sendToFileService("/files", HttpMethod.PUT, String.class);
    }
}
