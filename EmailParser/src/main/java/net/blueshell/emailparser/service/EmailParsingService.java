package net.blueshell.emailparser.service;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import net.blueshell.common.DTO.Event;
import net.blueshell.common.DTO.Image;
import net.blueshell.common.DTO.ParsedEmail;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmailParsingService {
    public ParsedEmail parseHTML(String content) {
        try {
            if (content == null || content.trim().isEmpty()) {
                throw new IllegalArgumentException("Newsletter content is empty or null.");
            }
            Document doc = Jsoup.parse(content);
            String plainText = doc.select("body").text();

            // String markdown = generateMarkdown(body); --> Too complex to transform atm - takes ages

            List<Image> images = extractImages(doc);
            List<Event> events = extractEvents(doc);

            return ParsedEmail
                    .builder()
                    .plainText(plainText)
                    .events(events)
                    .images(images)
                    .build();

        } catch (Exception e) {
            System.err.println("Error occurred during email parsing: " + e.getMessage());
            e.printStackTrace();

            // Return an empty ParsedEmail object or throw a custom exception if needed
            return ParsedEmail
                    .builder()
                    .plainText("")
                    .events(new ArrayList<>())
                    .images(new ArrayList<>())
                    .build();
        }
    }

    private String generateMarkdown(String content) {
        FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder().build();
        return converter.convert(content);
    }

    private List<Image> extractImages(Document doc) {
        List<Image> images = new ArrayList<>();

        for (Element img : doc.select("img")) {
            String src = img.attr("src");
            String alt = img.attr("alt");
            if (src.isEmpty()) continue;

            Image image = Image
                    .builder()
                    .url(src)
                    .title(alt)
                    .build();
            images.add(image);
        }

        return images;
    }

    private List<Event> extractEvents(Document doc) {
        Map<String, Event> events = new HashMap<>();
        List<Element> rows = doc.select("tr");

        for (int i = 0; i < rows.size() - 1; i++) {
            Element titleRow = rows.get(i);
            Element descRow = rows.get(i + 1);

            String title = titleRow.select("h4 span").text().trim();
            if (title.isEmpty()) continue;

            String description = descRow.select("p").text().trim();

            // Only add the event the first time we see a title with a description
            if (events.containsKey(title) || description.isEmpty()) continue;

            Event event = Event.builder()
                    .title(title)
                    .description(description)
                    .build();

            events.put(title, event);
            i++; // Skip next row since it's part of current event
        }

        return new ArrayList<>(events.values());
    }
}
