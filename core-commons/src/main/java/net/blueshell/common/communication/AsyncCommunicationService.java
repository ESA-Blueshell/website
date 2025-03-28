package net.blueshell.common.communication;

import net.blueshell.common.communication.communicators.*;
import net.blueshell.common.communication.communicators.base.CommunicatorBase;

public class AsyncCommunicationService implements IAsyncCommunicationService {

    @Override
    public String sendToAPIGateway(CommunicatorBase source, String body) {
        return source.sendAsync(new ApiGatewayCommunicator().getName(), body);
    }

    @Override
    public String sendToBlogService(CommunicatorBase source, String body) {
        return source.sendAsync(new BlogCommunicator().getName(), body);
    }

    @Override
    public String sendToEmailParserService(CommunicatorBase source, String body) {
        return source.sendAsync(new EmailParserCommunicator().getName(), body);
    }

    @Override
    public String sendToEventParserService(CommunicatorBase source, String body) {
        return source.sendAsync(new EventParserCommunicator().getName(), body);
    }

    @Override
    public String sendToSocialMediaService(CommunicatorBase source, String body) {
        return source.sendAsync(new SocialMediaCommunicator().getName(), body);
    }

    @Override
    public String sendToTelemetryService(CommunicatorBase source, String body) {
        return source.sendAsync(new TelemetryCommunicator().getName(), body);
    }
}
