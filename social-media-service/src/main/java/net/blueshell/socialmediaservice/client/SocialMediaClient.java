package net.blueshell.socialmediaservice.client;

import com.github.scribejava.apis.TwitterApi;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth1AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth10aService;
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

    private final String FACEBOOK_API_TEMPLATE = "https://graph.facebook.com/%s/feed?access_token=%s";

    @Value("${facebook.page.id}")
    private String facebookPageID;

    @Value("${facebook.access.token}")
    private String facebookAccessToken;

    @Value("${x.api.key}")
    private String twitterApiKey;

    @Value("${x.api.secret}")
    private String twitterApiSecret;

    @Value("${x.access.token}")
    private String twitterAccessToken;

    @Value("${x.access.secret}")
    private String twitterAccessTokenSecret;

    public void postToFacebook(SocialDTO dto, String link) {
        String url = String.format(
                FACEBOOK_API_TEMPLATE,
                facebookPageID,
                facebookAccessToken
        );

        HttpHeaders header = new HttpHeaders();
        header.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> payload = Map.of(
                "message", dto.getText(),
                "link", link
        );

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, header);
        ResponseEntity<String> res = restTemplate.postForEntity(url, entity, String.class);
    }

    public void postToTwitter(SocialDTO dto, String link) {
        try {
            OAuth10aService service = new ServiceBuilder(twitterApiKey)
                    .apiSecret(twitterApiSecret)
                    .build(TwitterApi.instance());

            OAuth1AccessToken accessToken = new OAuth1AccessToken(twitterAccessToken, twitterAccessTokenSecret);

            String tweet = dto.getText() + " " + link;

            OAuthRequest request = new OAuthRequest(Verb.POST, "https://api.twitter.com/1.1/statuses/update.json");
            request.addParameter("status", tweet);

            service.signRequest(accessToken, request);

            Response response = service.execute(request);
        } catch (Exception e) {
            System.err.println("Failed to post to X: " + e.getMessage());
        }
    }

    public void post(SocialDTO dto, PlatformType platform, String trackableURL) {
        switch (platform) {
            case FACEBOOK:
                postToFacebook(dto, trackableURL);
                return;
            case TWITTER:
                postToTwitter(dto, trackableURL);
                return;
            case LINKEDIN, INSTAGRAM:
            default:
                throw new IllegalArgumentException("Unsupported platform: " + platform);
        }
    }
}
