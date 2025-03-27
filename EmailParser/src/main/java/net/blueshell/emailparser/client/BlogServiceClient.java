package net.blueshell.emailparser.client;

import net.blueshell.common.ParsedEmail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class BlogServiceClient {

    @Autowired
    private RestTemplate restTemplate;

    private static final String BLOG_SERVICE_URL = "http://blog-service/api/blog";

    public void sendToBlogService(ParsedEmail parsedEmail) {
        restTemplate.postForObject(BLOG_SERVICE_URL, parsedEmail, Void.class);
    }
}
