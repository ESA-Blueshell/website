package net.blueshell.socialmediaservice.client;

import net.blueshell.common.dto.SocialDTO;
import net.blueshell.common.enums.PlatformType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class SocialMediaClient {
    @Autowired
    private RestTemplate restTemplate;

    @Value("${facebook.page.id")
    private String facebookPageID;
    @Value("${facebook.access.token")
    private String facebookAccessToken;
    private final String FACEBOOK_API_TEMPLATE = "https://graph.facebook.com/%s/feed?access_token=%s";

    public void postToFacebook(SocialDTO dto, String trackableURL) {
        String url = String.format(
                FACEBOOK_API_TEMPLATE,
                facebookPageID,
                facebookAccessToken
        );

        HttpHeaders header = new HttpHeaders();
        header.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> payload = Map.of(
                "message", dto.getText(),
                "link", trackableURL
        );

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, header);
        ResponseEntity<String> res = restTemplate.postForEntity(url, entity, String.class);

        System.out.println("Facebook post response: " + res.getBody());
    }

    public void postToTwitter(SocialDTO dto, String trackableURL) {
        // TODO
    }

    public void postToLinkedin(SocialDTO dto, String trackableURL) {
        // TODO
    }

    public void postToInstagram(SocialDTO dto, String trackableURL) {
        // TODO
    }

    public void post(SocialDTO dto, PlatformType platform, String trackableURL) {
        switch (platform) {
            case FACEBOOK:
                postToFacebook(dto, trackableURL);
                return;
            case TWITTER:
                postToTwitter(dto, trackableURL);
                return;
            case LINKEDIN:
                postToLinkedin(dto, trackableURL);
                return;
            case INSTAGRAM:
                postToInstagram(dto, trackableURL);
                return;
            default:
                throw new IllegalArgumentException("Unsupported platform: " + platform);
        }
    }
}
