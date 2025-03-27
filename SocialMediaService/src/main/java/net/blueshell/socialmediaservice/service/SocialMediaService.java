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
        String content = "📅 " + event.getTitle() + "\n" + event.getContent() + "\n" + link;

        post(content, link);
    }

    private void post(String content, String link) {
        socialMediaClient.postToFacebook(content, link);
        socialMediaClient.postToX(content, link);
    }

}
