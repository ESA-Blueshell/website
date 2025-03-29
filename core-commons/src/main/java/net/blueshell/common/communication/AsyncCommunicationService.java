package net.blueshell.common.communication;

import net.blueshell.common.communication.communicators.*;
import net.blueshell.common.communication.communicators.base.CommunicatorBase;
import net.blueshell.common.communication.communicators.base.ICommunicator;

public class AsyncCommunicationService implements IAsyncCommunicationService {

    @Override
    public String sendToAPIGateway(ICommunicator source, String body) {
        return source.sendAsync(ApiGatewayCommunicator.name, body);
    }

    @Override
    public String sendToBlogService(ICommunicator source, String body) {
        return source.sendAsync(BlogCommunicator.name, body);
    }

    @Override
    public String sendToEmailParserService(ICommunicator source, String body) {
        return source.sendAsync(EmailParserCommunicator.name, body);
    }

    @Override
    public String sendToEventParserService(ICommunicator source, String body) {
        return source.sendAsync(EventParserCommunicator.name, body);
    }

    @Override
    public String sendToSocialMediaService(ICommunicator source, String body) {
        return source.sendAsync(SocialMediaCommunicator.name, body);
    }

    @Override
    public String sendToTelemetryService(ICommunicator source, String body) {
        return source.sendAsync(TelemetryCommunicator.name, body);
    }
}
