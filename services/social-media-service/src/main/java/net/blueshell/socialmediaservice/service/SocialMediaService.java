package net.blueshell.socialmediaservice.service;

import net.blueshell.client.TelemetryClient;
import net.blueshell.dto.SocialDTO;
import net.blueshell.dto.TelemetryDTO;
import net.blueshell.enums.PlatformType;
import net.blueshell.socialmediaservice.client.SocialMediaClient;
import org.springframework.stereotype.Service;

@Service
public class SocialMediaService {
    
    private final TelemetryClient telemetryClient;
    private final SocialMediaClient socialMediaClient;

    public SocialMediaService(TelemetryClient telemetryClient, SocialMediaClient socialMediaClient) {
        this.telemetryClient = telemetryClient;
        this.socialMediaClient = socialMediaClient;
    }

    public void distribute(SocialDTO dto) {
        for (PlatformType platform : dto.getPlatforms()) {
            TelemetryDTO telemetry = telemetryClient.createTelemetry(platform, dto.getUrl());
            socialMediaClient.post(dto, platform, telemetry.getUrl());
        }
    }

//    private String generateEventContent(Event event, String link) {
//        StringBuilder sb = new StringBuilder();
//        sb.append("📅 *").append(event.getTitle()).append("*\n");
//
//        if (event.getLocation() != null && !event.getLocation().isEmpty()) {
//            sb.append("📍 ").append(event.getLocation()).append("\n");
//        }
//
//        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
//            sb.append("\n").append(event.getDescription()).append("\n");
//        }
//
//        sb.append("\n🔗 ").append(link);
//        return sb.toString();
//    }
}
