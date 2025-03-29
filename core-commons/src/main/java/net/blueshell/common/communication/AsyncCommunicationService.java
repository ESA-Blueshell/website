package net.blueshell.common.communication;

import net.blueshell.common.communication.communicators.*;
import net.blueshell.common.communication.communicators.base.CommunicatorBase;

public class AsyncCommunicationService implements IAsyncCommunicationService {

    @Override
    public String sendToAPIGateway(CommunicatorBase source, String body) {
        return source.sendAsync(ApiGatewayCommunicator.name, body);
    }

    @Override
    public String sendToBlogService(CommunicatorBase source, String body) {
        return source.sendAsync(BlogCommunicator.name, body);
    }

    @Override
    public String sendToEmailParserService(CommunicatorBase source, String body) {
        return source.sendAsync(EmailParserCommunicator.name, body);
    }

    @Override
    public String sendToEventParserService(CommunicatorBase source, String body) {
        return source.sendAsync(EventParserCommunicator.name, body);
    }

    @Override
    public String sendToSocialMediaService(CommunicatorBase source, String body) {
        return source.sendAsync(SocialMediaCommunicator.name, body);
    }

    @Override
    public String sendToTelemetryService(CommunicatorBase source, String body) {
        return source.sendAsync(TelemetryCommunicator.name, body);
    }
}
