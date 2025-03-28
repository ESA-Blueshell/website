package net.blueshell.socialmediaservice.service;

import net.blueshell.common.Blog;
import net.blueshell.common.Event;
import net.blueshell.socialmediaservice.client.SocialMediaClient;
import net.blueshell.socialmediaservice.client.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SocialMediaService {
    @Autowired
    private TelemetryClient telemetryClient;
    @Autowired
    private SocialMediaClient socialMediaClient;

    public void distributeBlog(Blog blog) {
        String link = telemetryClient.getTrackableBlogURL(blog.getId());
        String content = "Check out our new blog post:\n" + link;

        post(content, link);
    };

    public void distributeEvent(Event event) {
        String link = telemetryClient.getTrackableEventURL(event.getId());
        String content = generateEventContent(event, link);

        post(content, link);
    }

    private void post(String content, String link) {
        socialMediaClient.postToFacebook(content, link);
        socialMediaClient.postToX(content, link);
    }

    private String generateEventContent(Event event, String link) {
        StringBuilder sb = new StringBuilder();
        sb.append("📅 *").append(event.getTitle()).append("*\n");

        if (event.getLocation() != null && !event.getLocation().isEmpty()) {
            sb.append("📍 ").append(event.getLocation()).append("\n");
        }

        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            sb.append("\n").append(event.getDescription()).append("\n");
        }

        sb.append("\n🔗 ").append(link);
        return sb.toString();
    }
}
