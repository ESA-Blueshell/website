//package net.blueshell.api.service;
//
//import net.blueshell.api.client.SocialMediaClient;
//import net.blueshell.api.common.enums.PlatformType;
//import net.blueshell.api.dto.SocialDTO;
//import net.blueshell.api.model.Telemetry;
//import org.springframework.stereotype.Service;
//
//@Service
//public class SocialMediaService {
//
//    private final TelemetryService telemetryService;
//    private final SocialMediaClient socialMediaClient;
//
//    public SocialMediaService(TelemetryService telemetryService, SocialMediaClient socialMediaClient) {
//        this.telemetryService = telemetryService;
//        this.socialMediaClient = socialMediaClient;
//    }
//
//    public void distribute(SocialDTO dto) {
//        for (PlatformType platform : dto.getPlatforms()) {
//            Telemetry telemetry = telemetryService.createTelemetry(platform, dto.getUrl());
//            socialMediaClient.post(dto, platform, telemetry.getUrl());
//        }
//    }
//}
